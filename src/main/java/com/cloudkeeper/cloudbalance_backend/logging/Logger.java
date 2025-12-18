package com.cloudkeeper.cloudbalance_backend.logging;

public interface Logger {
    void debug(String message, Object... args);
    void info(String message, Object... args);
    void warn(String message, Object... args);
    void error(String message, Object... args);
    void error(String message, Throwable throwable ,Object... args);
    void trace(String message, Object... args);
}
