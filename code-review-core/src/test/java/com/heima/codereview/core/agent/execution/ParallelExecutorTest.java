package com.heima.codereview.core.agent.execution;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParallelExecutorTest {

    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    private final ParallelExecutor parallelExecutor = new ParallelExecutor(executorService);

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
    }

    @Test
    void executeReturnsPartialResultsWhenTimeoutOccurs() {
        long start = System.currentTimeMillis();

        List<ParallelExecutor.ParallelExecutionResult<Integer, String>> results = parallelExecutor.execute(
                List.of(100, 2_000),
                delayMs -> {
                    sleep(delayMs);
                    return "done-" + delayMs;
                },
                Duration.ofMillis(300)
        );

        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 1_000, "timeout should not wait for the slow task to finish");
        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(result -> result.success() && "done-100".equals(result.result())));
        assertTrue(results.stream().anyMatch(ParallelExecutor.ParallelExecutionResult::timedOut));
    }

    @Test
    void executeCapturesTaskFailureWithoutDroppingSuccessfulResults() {
        List<ParallelExecutor.ParallelExecutionResult<String, String>> results = parallelExecutor.execute(
                List.of("ok", "fail"),
                value -> {
                    if ("fail".equals(value)) {
                        throw new IllegalStateException("boom");
                    }
                    return value.toUpperCase();
                },
                Duration.ofSeconds(1)
        );

        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(result -> result.success() && "OK".equals(result.result())));
        assertTrue(results.stream().anyMatch(result -> !result.success()
                && !result.timedOut()
                && result.error() instanceof IllegalStateException
                && "boom".equals(result.error().getMessage())));
        assertFalse(results.stream().anyMatch(ParallelExecutor.ParallelExecutionResult::timedOut));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
