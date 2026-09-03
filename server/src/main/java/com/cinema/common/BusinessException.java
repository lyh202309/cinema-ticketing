package com.cinema.common;

/**
 * 业务异常：返回 code=0 + msg
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String msg) {
        super(msg);
    }
}
