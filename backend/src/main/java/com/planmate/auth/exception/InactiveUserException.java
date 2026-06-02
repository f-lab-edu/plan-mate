package com.planmate.auth.exception;

public class InactiveUserException extends RuntimeException {

    public InactiveUserException() {
        super("Inactive user cannot login.");
    }

}

