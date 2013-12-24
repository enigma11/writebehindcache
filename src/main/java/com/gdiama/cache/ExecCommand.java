package com.gdiama.cache;

public interface ExecCommand {

    <T> void run(T event);
}
