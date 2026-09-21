package com.cinema.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 消息（永久存档）
 */
@Data
@TableName("tb_chat_message")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    /** 0 用户 / 1 助手 */
    private Integer role;

    private String content;

    /** 助手消息引用的 FAQ 标题（JSON 数组字符串），仅 role=1 可能有值 */
    private String sources;

    private LocalDateTime createTime;
}
