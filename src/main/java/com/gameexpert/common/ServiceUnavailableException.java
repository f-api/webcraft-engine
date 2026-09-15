package com.gameexpert.common;

import lombok.Getter;

/** The application has not completed a required startup authority boundary. */
@Getter
public class ServiceUnavailableException extends RuntimeException {

    private final String error;

    public ServiceUnavailableException(String error) {
        super(error);
        this.error = error;
    }
}
