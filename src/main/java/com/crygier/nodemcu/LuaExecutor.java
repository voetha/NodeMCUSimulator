package com.crygier.nodemcu;

import org.luaj.vm2.LuaError;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

class LuaExecutor implements ScheduledExecutorService {
    ScheduledExecutorService errorHandler = Executors.newSingleThreadScheduledExecutor();
    ScheduledExecutorService underlying = Executors.newSingleThreadScheduledExecutor();

    protected void handle(LuaError e) {
        //TODO: better error handling
        System.err.println(e.getMessageObject());
    }

    protected Runnable handlingLuaError(Runnable command) {
        return () -> {
            try {
                command.run();
            } catch(LuaError e) {
                errorHandler.execute(() -> handle(e));
            }
        };
    }

    protected <V> Callable<V> handlingLuaError(Callable<V> callable) {
        return () -> {
            try {
                return callable.call();
            } catch(LuaError e) {
                errorHandler.execute(() -> handle(e));
                throw e;
            }
        };
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return underlying.schedule(handlingLuaError(command), delay, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return underlying.schedule(handlingLuaError(callable), delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return underlying.scheduleAtFixedRate(handlingLuaError(command), initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return underlying.scheduleWithFixedDelay(handlingLuaError(command), initialDelay, delay, unit);
    }

    @Override
    public void shutdown() {
        underlying.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return underlying.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return underlying.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return underlying.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return underlying.awaitTermination(timeout, unit);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return underlying.submit(handlingLuaError(task));
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return underlying.submit(handlingLuaError(task), result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return underlying.submit(handlingLuaError(task));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return underlying.invokeAll(tasks.stream().map(this::handlingLuaError).collect(Collectors.toList()));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        return underlying.invokeAll(tasks.stream().map(this::handlingLuaError).collect(Collectors.toList()), timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return underlying.invokeAny(tasks.stream().map(this::handlingLuaError).collect(Collectors.toList()));
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return underlying.invokeAny(tasks.stream().map(this::handlingLuaError).collect(Collectors.toList()), timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        underlying.execute(this.handlingLuaError(command));
    }
}
