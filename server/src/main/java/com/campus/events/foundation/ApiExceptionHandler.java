package com.campus.events.foundation;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler(ApiException.class) public ResponseEntity<Object> business(ApiException ex) {
        return Responses.http(Responses.error(ex.status(), ex.code(), ex.getMessage(), ex.details()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingRequestHeaderException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<Object> invalid(Exception ex) {
        return Responses.http(Responses.error(400, "VALIDATION_ERROR", "请求参数缺失或格式不正确", java.util.Map.of()));
    }
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<Object> missing(Exception ex) { return business(ApiException.notFound()); }
    @ExceptionHandler(Exception.class) public ResponseEntity<Object> unexpected(Exception ex) {
        LOG.error("请求处理失败，requestId={}", RequestIds.current(), ex);
        return Responses.http(Responses.error(500, "INTERNAL_ERROR", "服务暂时无法完成操作", java.util.Map.of()));
    }
}
