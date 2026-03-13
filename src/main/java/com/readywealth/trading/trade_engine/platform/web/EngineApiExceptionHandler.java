package com.readywealth.trading.trade_engine.platform.web;

import com.readywealth.trading.trade_engine.engine.api.http.EngineRuntimeController;
import com.readywealth.trading.trade_engine.session.domain.SessionForbiddenException;
import com.readywealth.trading.trade_engine.session.domain.SessionNotFoundException;
import com.readywealth.trading.trade_engine.session.domain.SessionUnauthorizedException;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = EngineRuntimeController.class)
public class EngineApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> onBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> onIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(SessionUnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> onUnauthorized(SessionUnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(SessionForbiddenException.class)
    public ResponseEntity<Map<String, Object>> onForbidden(SessionForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> onNotFound(SessionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }
}
