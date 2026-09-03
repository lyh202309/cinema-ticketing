package com.cinema.common;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

/**
 * 全局异常处理
 * - BusinessException  → code=0 + msg
 * - ForbiddenException → HTTP 403 + msg（非法操作）
 * - 其他              → 系统异常兜底
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        return Result.fail(e.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Result<Void>> handleForbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.fail(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public Object handleException(Exception e, HttpServletResponse response) {
        // SSE 长连接断连/客户端中止(刷新页面、切走、关标签页)是正常生命周期噪音，不是服务端 bug，静默忽略
        if (isClientAbort(e)) {
            return null;
        }
        log.error("系统异常", e);
        // SSE 响应已提交(异步阶段)时无法再写 JSON，避免二次 HttpMessageNotWritableException
        if (response.isCommitted()) {
            return null;
        }
        return Result.fail("系统异常，请稍后重试");
    }

    /** 判断是否为客户端断开连接引发的异常（沿 cause 链查找） */
    private boolean isClientAbort(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof AsyncRequestNotUsableException || cur instanceof ClientAbortException) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }
}
