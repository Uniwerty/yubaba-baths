package ru.yubaba.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.yubaba.service.BusinessException;

import java.util.Map;

@RestControllerAdvice
public class ServerErrorHandler {

    private ResponseEntity<?> buildErrorResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<?> handleBusinessException(BusinessException e) {
        return buildErrorResponse(e.status, e.getMessage());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<?> handleValidationException(Exception e) {
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Проверьте обязательные поля, формат и допустимые значения"
        );
    }

    @ExceptionHandler({
            DataIntegrityViolationException.class,
            OptimisticLockingFailureException.class,
            PessimisticLockingFailureException.class
    })
    ResponseEntity<?> handleConflictException(Exception e) {
        return buildErrorResponse(
                HttpStatus.CONFLICT,
                "Данные уже изменены или такое значение существует. Обновите страницу и повторите действие"
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<?> handleAccessException(Exception e) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Недостаточно прав для этого действия");
    }
}
