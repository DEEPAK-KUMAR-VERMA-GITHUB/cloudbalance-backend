package com.cloudkeeper.cloudbalance_backend.logging;

public enum LogLevel {
    TRACE(0),
    DEBUG(1),
    INFO(2),
    WARN(3),
    ERROR(4),
    OFF(5);

    private final int level;

    LogLevel(int level){
        this.level = level;
    }

    public boolean isLoggable(LogLevel targetLevel){
        return this.level <= targetLevel.level;
    }

}
