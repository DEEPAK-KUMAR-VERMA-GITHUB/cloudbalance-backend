package com.cloudkeeper.cloudbalance_backend.exception;

public class MaxSessionsReachedException extends RuntimeException {
    public MaxSessionsReachedException(String message) {
        super(message);
    }
}
