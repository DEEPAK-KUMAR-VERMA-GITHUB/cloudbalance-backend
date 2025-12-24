package com.cloudkeeper.cloudbalance_backend.exception;

public class ResourceAlreadyExistsException extends RuntimeException {
    public ResourceAlreadyExistsException(String message){
        super(message);
    }
}
