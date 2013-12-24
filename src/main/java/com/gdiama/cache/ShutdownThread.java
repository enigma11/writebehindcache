package com.gdiama.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
    Register this shutdown hook:

    Runtime.getRuntime()
                .addShutdownHook(
                        new ShutdownThread(shutdownService));
 */

public class ShutdownThread extends Thread {

    private final static Logger LOGGER = LoggerFactory.getLogger(ShutdownThread.class);
    private final ShutdownService shutdownService;

    public ShutdownThread(ShutdownService shutdownService) {
        this.shutdownService = shutdownService;
    }

    @Override
    public void run() {
        LOGGER.info("SHUTDOWN THREAD HAS BEEN STARTED");
        shutdownService.shutdownServices();
    }
}
