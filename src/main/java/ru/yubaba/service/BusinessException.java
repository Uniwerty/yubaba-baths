package ru.yubaba.service;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {
    public final HttpStatus status;

    public BusinessException(String message) {
        this(HttpStatus.CONFLICT, message);
    }

    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
