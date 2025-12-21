package com.cloudkeeper.cloudbalance_backend.exception;

public class ResourceAlreadyExistsExceptions extends RuntimeException {
    public ResourceAlreadyExistsExceptions(String message){
        super(message);
    }
}
