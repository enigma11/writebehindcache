package com.gdiama.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ShutdownService {

    private final static Logger LOGGER = LoggerFactory.getLogger(ShutdownService.class);
    private final static List<Lifcycle.Shutdownable> SHUTDOWNABLES = new ArrayList<>();

    public void shutdownServices() {
        for (Lifcycle.Shutdownable shutdownable : SHUTDOWNABLES) {
            try {
                LOGGER.info("Shutting down {}", shutdownable);
                shutdownable.shutdown();
                LOGGER.info("Shut down {}", shutdownable);
            } catch (Exception e) {
                LOGGER.info("Error while shutting down {}", shutdownable);
            }
        }
    }

    public void register(Lifcycle.Shutdownable shutdownable) {
        LOGGER.info("Registered shutdownable {}", shutdownable);
        SHUTDOWNABLES.add(shutdownable);
    }
}
