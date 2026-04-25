package com.heima.codereview.core.agent.execution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

public class ParallelExecutor {

    private static final Logger log = LoggerFactory.getLogger(ParallelExecutor.class);

    private final Executor executor;

    public ParallelExecutor(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    public <T, R> List<ParallelExecutionResult<T, R>> execute(
            List<T> tasks,
            Function<T, R> taskExecutor,
            Duration timeout) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        Objects.requireNonNull(taskExecutor, "taskExecutor must not be null");

        Duration effectiveTimeout = timeout == null || timeout.isNegative()
                ? Duration.ZERO
                : timeout;

        List<TrackedFuture<T, R>> trackedFutures = tasks.stream()
                .map(task -> {
                    CompletableFuture<R> sourceFuture = CompletableFuture.supplyAsync(() -> taskExecutor.apply(task), executor);
                    CompletableFuture<ParallelExecutionResult<T, R>> resultFuture = sourceFuture
                            .handle((result, throwable) -> {
                                if (throwable != null) {
                                    return ParallelExecutionResult.failed(task, unwrap(throwable));
                                }
                                return ParallelExecutionResult.completed(task, result);
                            });
                    return new TrackedFuture<>(task, sourceFuture, resultFuture);
                })
                .toList();

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                trackedFutures.stream()
                        .map(TrackedFuture::resultFuture)
                        .toArray(CompletableFuture[]::new)
        );

        boolean timedOut = false;
        try {
            allFutures.get(effectiveTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            timedOut = true;
            log.warn("Parallel execution timeout after {} ms", effectiveTimeout.toMillis());
        } catch (InterruptedException e) {
            timedOut = true;
            Thread.currentThread().interrupt();
            log.warn("Parallel execution interrupted");
        } catch (ExecutionException e) {
            log.warn("Parallel execution failed while waiting for completion", e);
        }

        List<ParallelExecutionResult<T, R>> results = new ArrayList<>();
        for (TrackedFuture<T, R> trackedFuture : trackedFutures) {
            CompletableFuture<ParallelExecutionResult<T, R>> future = trackedFuture.resultFuture();
            if (future.isDone()) {
                ParallelExecutionResult<T, R> result = future.getNow(null);
                if (result != null) {
                    results.add(result);
                }
                continue;
            }

            if (timedOut) {
                trackedFuture.sourceFuture().cancel(true);
                future.cancel(true);
                results.add(ParallelExecutionResult.timeout(trackedFuture.task()));
            }
        }
        return results;
    }

    private static Throwable unwrap(Throwable throwable) {
        if ((throwable instanceof CompletionException || throwable instanceof ExecutionException)
                && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    private record TrackedFuture<T, R>(
            T task,
            CompletableFuture<R> sourceFuture,
            CompletableFuture<ParallelExecutionResult<T, R>> resultFuture
    ) {
    }

    public record ParallelExecutionResult<T, R>(
            T task,
            R result,
            Throwable error,
            boolean timedOut
    ) {

        public static <T, R> ParallelExecutionResult<T, R> completed(T task, R result) {
            return new ParallelExecutionResult<>(task, result, null, false);
        }

        public static <T, R> ParallelExecutionResult<T, R> failed(T task, Throwable error) {
            return new ParallelExecutionResult<>(task, null, error, false);
        }

        public static <T, R> ParallelExecutionResult<T, R> timeout(T task) {
            return new ParallelExecutionResult<>(task, null, null, true);
        }

        public boolean success() {
            return !timedOut && error == null && result != null;
        }
    }
}
