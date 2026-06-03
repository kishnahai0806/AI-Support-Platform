package com.krish.supportapi.exception;

public class TicketClosedException extends RuntimeException {

    public TicketClosedException(String message) {
        super(message);
    }
}
