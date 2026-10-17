package com.example.smartwaste.web;

/** The request is valid but clashes with current state (duplicate code, route already dispatched, ...). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
