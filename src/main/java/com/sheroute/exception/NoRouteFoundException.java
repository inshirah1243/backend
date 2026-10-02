package com.sheroute.exception;

public class NoRouteFoundException extends RuntimeException {

    public NoRouteFoundException(String message) {
        super(message);
    }
}
