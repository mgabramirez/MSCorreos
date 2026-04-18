package com.acosux.MSCorreos.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class EmailYaExisteException extends RuntimeException {
    
    public EmailYaExisteException(String message) {
        super(message);
    }
}
