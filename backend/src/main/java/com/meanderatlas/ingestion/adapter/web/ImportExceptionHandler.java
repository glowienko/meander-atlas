package com.meanderatlas.ingestion.adapter.web;

import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.meanderatlas.ingestion.adapter.web")
class ImportExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleBadRequest(IllegalArgumentException ex) {
        String detail = Objects.requireNonNullElse(ex.getMessage(), "Invalid import request");
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }
}
