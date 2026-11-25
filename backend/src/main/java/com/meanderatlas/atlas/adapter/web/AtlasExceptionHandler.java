package com.meanderatlas.atlas.adapter.web;

import java.util.NoSuchElementException;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.meanderatlas.atlas.adapter.web")
class AtlasExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleBadRequest(IllegalArgumentException ex) {
        String detail = Objects.requireNonNullElse(ex.getMessage(), "Invalid atlas request");
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail handleNotFound(NoSuchElementException ex) {
        String detail = Objects.requireNonNullElse(ex.getMessage(), "Atlas resource not found");
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, detail);
    }
}
