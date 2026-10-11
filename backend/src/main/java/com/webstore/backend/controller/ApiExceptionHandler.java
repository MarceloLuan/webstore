package com.webstore.backend.controller;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpHeaders;
import com.webstore.backend.service.RateLimitException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<Map<String, String>> handleRateLimit(RateLimitException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.getRetryAfterSeconds()))
                .body(Map.of("message", exception.getReason()));
    }

    @ExceptionHandler(com.webstore.backend.service.frete.FreteException.class)
    public ResponseEntity<Map<String, String>> handleFrete(com.webstore.backend.service.frete.FreteException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("code", exception.getCodigo(), "message", exception.getReason()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException exception) {
        String message = exception.getReason();
        if (message == null || message.isBlank()) {
            message = "Não foi possível concluir a requisição.";
        }

        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        return ResponseEntity.status(status).body(Map.of("message", message));
    }
}
