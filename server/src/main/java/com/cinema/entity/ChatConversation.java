package com.cinema.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话
 */
@Data
@TableName("tb_chat_conversation")
public class ChatConversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 首条用户消息摘要（会话列表展示） */
    private String title;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
