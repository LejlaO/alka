package com.alkadev.utils.common;

/**
 * @author : haris
 * @since : 5.12.20., Sat
 **/
public class AlkaException extends Exception {
    public AlkaException(){super();}

    public AlkaException(String message) {
            super(message);
        }

    public AlkaException(String message, Throwable reason) {
            super(message, reason);
        }
}
