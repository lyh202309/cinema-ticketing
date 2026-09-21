package com.cinema.chat.embedding;

import cn.hutool.core.util.StrUtil;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * rag/*.md 知识库的加载与切分
 *
 * 切分约定（写知识库时必须遵守）：
 * - `# ` 一级标题 = 文件级分类，会作为检索文本的上下文前缀
 * - `## ` 二级标题 = 一个片段（chunk），标题同时进 Metadata 与向量文本
 * - 每个小节正文 100~300 字：太短检索信号弱，太长挤占模型上下文
 *
 * 单独成类而不是塞进 FaqVectorInitRunner：切分有三个容易踩的边界（见 split 的注释），
 * 值得独立验证；Runner 只负责启动编排。
 */
@Slf4j
public final class FaqMarkdownSplitter {

    private FaqMarkdownSplitter() {
    }

    /**
     * 从 classpath 加载并切分全部知识库文件。
     *
     * 【为什么用显式文件名清单，而不是 ClassPathDocumentLoader 扫目录】
     * DocumentLoader 依赖 ClassPathSource.isInsideArchive()（实现是判断 url 协议是否等于 "jar"）
     * 加 FileSystems.newFileSystem，而 Spring Boot 3.2+ 重打包后的 URL 是
     * jar:nested:/app.jar/!BOOT-INF/classes/!/rag，zipfs 解析不了会抛 FileSystemNotFoundException；
     * 而且 jar 内若没有 rag/ 目录条目，getResource("rag") 直接返回 null。
     * 最坑的地方在于：mvn spring-boot:run 和 IDE 跑的是 target/classes 目录，一切正常，
     * 一旦 mvn package && java -jar 就静默变成空知识库。显式清单两个场景都稳。
     */
    public static List<TextSegment> loadAll(String[] files, String dir) throws IOException {
        List<TextSegment> segments = new ArrayList<>();
        for (String file : files) {
            ClassPathResource resource = new ClassPathResource(dir + file);
            if (!resource.exists()) {
                log.warn("[RAG] 知识库文件缺失：{}{}", dir, file);
                continue;
            }
            String markdown;
            try (InputStream in = resource.getInputStream()) {
                // 显式 UTF-8：内容是中文，不能依赖平台默认编码
                markdown = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            segments.addAll(split(markdown, file));
        }
        return segments;
    }

    /**
     * 按行扫描切分：每个 ## 二级标题开一个新片段。
     *
     * 三个必须处理的边界：
     * ① 首个 ## 之前的内容（一级标题、引言、HTML 注释）只用于填 category，不当正文
     * ② 代码块围栏内的 ## 不能切（FAQ 里可能要画座位图）
     * ③ 空小节跳过：没有检索价值，还会污染 df 统计
     */
    public static List<TextSegment> split(String markdown, String source) {
        List<TextSegment> out = new ArrayList<>();
        String category = "";
        String title = null;
        StringBuilder body = new StringBuilder();
        boolean inCodeFence = false;

        for (String line : markdown.split("\r\n|\r|\n", -1)) {
            String trimmed = line.trim();

            if (trimmed.startsWith("```")) {
                inCodeFence = !inCodeFence;
                if (title != null) {
                    body.append(line).append('\n');
                }
                continue;
            }
            if (!inCodeFence) {
                if (trimmed.startsWith("# ")) {
                    category = trimmed.substring(2).trim();
                    continue;
                }
                if (trimmed.startsWith("## ")) {
                    appendSegment(out, source, category, title, body);
                    title = trimmed.substring(3).trim();
                    body.setLength(0);
                    continue;
                }
            }
            // title 仍为 null 说明还在文件头（分类标题与引言区），不属于任何片段
            if (title != null) {
                body.append(line).append('\n');
            }
        }
        appendSegment(out, source, category, title, body);
        return out;
    }

    private static void appendSegment(List<TextSegment> out, String source, String category,
                                      String title, StringBuilder body) {
        if (title == null) {
            return;
        }
        String text = body.toString().trim();
        body.setLength(0);
        if (StrUtil.isBlank(text)) {
            return;
        }
        // 标题必须进向量文本，不能只放 Metadata：
        // 「退票怎么弄」这类短问句主要就是靠标题的字面特征命中的
        String embeddingText = "【" + category + " > " + title + "】\n" + text;
        Metadata metadata = Metadata.from(Map.of(
                "title", title,
                "source", source,
                "category", category));
        out.add(TextSegment.from(embeddingText, metadata));
    }
}
