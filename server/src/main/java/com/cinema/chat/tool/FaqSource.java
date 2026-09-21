package com.cinema.chat.tool;

/**
 * FAQ 检索命中的来源，用于落库到 tb_chat_message.sources 并在前端展示「依据」
 *
 * @param title 命中的 FAQ 小节标题
 * @param score 该片段在各条检索路中的【最高查询词覆盖率】（0~1），
 *              也就是守门用的那个指标本身。刻意用覆盖率而不是余弦相似度：
 *              余弦在本场景只有 0.10~0.25 且与相关性重叠，写进日志或展示都无法判断好坏；
 *              覆盖率则直接可读 —— 0.65 就是「问题里六成半的信息量在片段里找到了」。
 *              理由详见 LocalHashEmbeddingModel#coverage
 */
public record FaqSource(String title, double score) {
}
