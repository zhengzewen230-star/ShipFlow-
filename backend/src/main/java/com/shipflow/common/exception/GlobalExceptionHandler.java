package com.shipflow.common.exception;

import com.shipflow.common.api.ApiErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.shipflow.auth.service.LoginIdentityAuthenticationException;
import com.shipflow.security.refresh.RefreshTokenAuthenticationException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(LoginIdentityAuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleLoginFailure(LoginIdentityAuthenticationException exception) {
        return response(HttpStatus.UNAUTHORIZED, new ApiErrorResponse("AUTH-1001", "Authentication failed"));
    }

    @ExceptionHandler(RefreshTokenAuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleRefreshFailure(RefreshTokenAuthenticationException exception) {
        return response(HttpStatus.UNAUTHORIZED, new ApiErrorResponse("AUTH-1002", "Refresh authentication failed"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            details.put(error.getField(), error.getDefaultMessage());
        }
        return response(HttpStatus.BAD_REQUEST,
                new ApiErrorResponse("COMMON-1001", "请求参数错误", details));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
        return response(HttpStatus.BAD_REQUEST,
                new ApiErrorResponse("COMMON-1001", "请求参数错误"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException exception) {
        return response(HttpStatus.BAD_REQUEST,
                new ApiErrorResponse("COMMON-1008", "请求体格式错误"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR,
                new ApiErrorResponse("COMMON-1007", "内部系统错误"));
    }

    private ResponseEntity<ApiErrorResponse> response(HttpStatus status, ApiErrorResponse body) {
        return ResponseEntity.status(status).body(body);
    }
}
