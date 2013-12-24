package com.gdiama.cache;

import com.codahale.metrics.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.lang.String.format;

public class WriteBehindCache<T> implements Lifcycle.Shutdownable {

    private final Logger logger;

    private final BlockingQueue<T> objects = new LinkedBlockingDeque<>();

    private final ScheduledExecutorService flushScheduler;
    private final ExecutorService executor;
    private final List<ExecutorShutdown> shutdownHooks = new ArrayList<>();
    private final Counter storageCounter;
    private final Counter flushCounter;
    private final Meter storageMeter;
    private final Meter flushMeter;
    private final Timer storageTimer;
    private final Timer flushTimer;

    private final ScheduledFuture<?> scheduledFuture;
    private final ExecCommand command;

    public WriteBehindCache(ExecCommand command, String objectName, AppConfiguration config, ShutdownService shutdownService, MetricRegistry metricRegistry) {
        this.command = command;
        this.logger = LoggerFactory.getLogger(objectName);
        this.flushScheduler = Executors.newScheduledThreadPool(1);
        this.scheduledFuture = this.flushScheduler.scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
                        flush();
                    }
                },
                config.initDelayWriteBehindCacheInMillis(),
                config.writeBehindFlushInterval(),
                config.writeBehindFlushTimeUnit());
        ((ScheduledThreadPoolExecutor) flushScheduler).setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        ((ScheduledThreadPoolExecutor) flushScheduler).setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        ((ScheduledThreadPoolExecutor) flushScheduler).setRemoveOnCancelPolicy(true);

        this.executor = Executors.newFixedThreadPool(10);
        this.shutdownHooks.add(new ExecutorShutdown(flushScheduler, config));
        this.shutdownHooks.add(new ExecutorShutdown(executor, config));

        shutdownService.register(this);

        storageCounter = metricRegistry.counter(objectName + ".writebehindcache.storage.counter");
        storageMeter = metricRegistry.meter(objectName + ".writebehindcache.storage.meter");
        storageTimer = metricRegistry.timer(objectName + ".writebehindcache.storage.timer");

        flushCounter = metricRegistry.counter(objectName + ".writebehindcache.flushed.counter");
        flushMeter = metricRegistry.meter(objectName + ".writebehindcache.flushed.meter");
        flushTimer = metricRegistry.timer(objectName + ".writebehindcache.flushed.timer");

        metricRegistry.register(objectName + ".cache.size", new Gauge<Integer>() {
            @Override
            public Integer getValue() {
                return objects.size();
            }
        });
    }

    public void store(T obj) {
        if(executor.isTerminated() || flushScheduler.isTerminated()) {
            throw new IllegalStateException("Shut down");
        }
        storageCounter.inc();
        storageMeter.mark();
        logger.debug("Storing {}. Total {}", obj, storageCounter.getCount());

        Timer.Context context = storageTimer.time();
        try {
            objects.put(obj);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            context.stop();
        }
    }

    public void shutdown() throws Exception {
        for (ExecutorShutdown hook : shutdownHooks) {
            try {
                hook.shutdown();
                scheduledFuture.cancel(true);
            } catch (Exception e) {
                logger.info("Error while shutting down {}", hook);
            }
        }
    }

    private void flush() {
        final List<T> objectsToFlush = new ArrayList<>();
        int noOfObjectsToFlush;

        objects.drainTo(objectsToFlush);
        noOfObjectsToFlush = objectsToFlush.size();


        logger.info("Flushing {} objects", noOfObjectsToFlush);
        List<Future<Void>> tasks = new ArrayList<>();
        long start = System.currentTimeMillis();

        for (final T object : objectsToFlush) {
            try {
                tasks.add(executor.submit(new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        Timer.Context context = flushTimer.time();
                        try {
                            doWork(object);
                        } finally {
                            context.stop();
                            flushCounter.inc();
                            flushMeter.mark();
                        }
                        return null;
                    }


                }));
            } catch (Exception e) {
                logger.warn(format("Problem flushing object %s", object), e);
            }
        }

        final AtomicInteger success = new AtomicInteger();
        boolean interrupted = false;
        for (Future<Void> task : tasks) {
            try {
                task.get(5000, TimeUnit.MILLISECONDS);
                success.incrementAndGet();
            } catch (InterruptedException e) {
                interrupted = true;
            } catch (ExecutionException e) {
                logger.warn("Exception raised while doing work", e.getCause());
            } catch (TimeoutException e) {
                task.cancel(true);
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        long end = System.currentTimeMillis();
        long duration = end - start;
        logger.info("Finished flushing cycle in {}ms. Objects flushed successfully {}/{}", duration, success.get(), noOfObjectsToFlush);
    }

    private void doWork(T object) {
        command.run(object);
    }
}
