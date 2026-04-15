package com.heima.codereview.api.exception;

import com.heima.codereview.common.exception.BizException;
import com.heima.codereview.common.result.ApiResponse;
import com.heima.codereview.common.result.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBiz(BizException e) {
        return ApiResponse.fail(ResultCode.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleSystem(Exception e) {
        log.error("系统异常", e);
        return ApiResponse.fail(ResultCode.SERVER_ERROR, "系统繁忙，请稍后再试");
    }
}
