package com.gdiama.cache;

import java.util.concurrent.ExecutorService;

public class ExecutorShutdown implements Lifcycle.Shutdownable {

    private final ExecutorService executor;
    private final AppConfiguration config;

    public ExecutorShutdown(ExecutorService executor, AppConfiguration config) {
        this.executor = executor;
        this.config = config;
    }


    @Override
    public void shutdown() throws Exception {
        executor.shutdown();
        if (!executor.awaitTermination(config.awaitTerminationDuration(), config.awaitTerminationTimeUnit())) {
            executor.shutdownNow();
        }
    }
}
