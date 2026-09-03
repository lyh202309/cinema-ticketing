package com.cinema.common;

/**
 * 越权/非法操作异常：返回 HTTP 403 + msg（接口文档语义：无资格访问热门场次座位图/锁座等）
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String msg) {
        super(msg);
    }
}
