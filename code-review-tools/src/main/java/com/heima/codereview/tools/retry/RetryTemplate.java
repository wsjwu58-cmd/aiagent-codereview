package com.heima.codereview.tools.retry;

import com.heima.codereview.tools.exception.ToolExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;

@Component("toolRetryTemplate")
public class RetryTemplate {

    private static final Logger log = LoggerFactory.getLogger(RetryTemplate.class);

    public <T> T execute(String operationName, RetryStrategy strategy, Callable<T> callable) {
        int maxAttempts = strategy.maxAttempts();
        long delay = strategy.initialDelayMs();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return callable.call();
            } catch (Exception e) {
                if (attempt >= maxAttempts) {
                    throw new ToolExecutionException("工具执行失败，已达到最大重试次数: " + operationName, e);
                }
                log.warn("工具执行失败，准备重试。操作: {}，第 {} 次", operationName, attempt);
                sleep(strategy, delay);
                if (strategy == RetryStrategy.EXPONENTIAL_BACKOFF) {
                    delay *= 2;
                }
            }
        }
        throw new ToolExecutionException("工具执行失败: " + operationName);
    }

    private void sleep(RetryStrategy strategy, long delay) {
        if (strategy == RetryStrategy.IMMEDIATE || delay <= 0) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolExecutionException("重试等待被中断", e);
        }
    }
}
