package com.acosux.MSCorreos.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class EmailNoEncontradoException extends RuntimeException {
    
    public EmailNoEncontradoException(String message) {
        super(message);
    }
}
