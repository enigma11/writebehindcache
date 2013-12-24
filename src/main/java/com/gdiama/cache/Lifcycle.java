package com.gdiama.cache;


public interface Lifcycle {
    public interface Shutdownable {
        void shutdown() throws Exception;
    }
}
