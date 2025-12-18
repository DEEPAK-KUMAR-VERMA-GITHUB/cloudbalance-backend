package com.cloudkeeper.cloudbalance_backend.logging;

import java.util.concurrent.ConcurrentHashMap;

public class LoggerFactory {
    private static final ConcurrentHashMap<String, Logger> loggers = new ConcurrentHashMap<>();

    public static Logger getLogger(Class<?> classs) {
        return loggers.computeIfAbsent(classs.getName(), k -> new MyLogger(classs));
    }

    public static Logger getLogger(String name) {
        return loggers.computeIfAbsent(name, k -> {
            try {
                return new MyLogger(Class.forName(name));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Can not crate logger for : " + name, e);
            }
        });
    }

}
