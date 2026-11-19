package com.gsx.shop.common.web;

import com.gsx.shop.common.exception.*;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

/** HTTP 边界统一映射；技术异常只记录类型，不回传 SQL、连接地址或请求正文。 */
@RestControllerAdvice
public class ShopExceptionHandler {
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<?> missing(Exception e) {
        return business(new ShopException(ShopError.NOT_FOUND));
    }

    @ExceptionHandler(org.springframework.web.bind.ServletRequestBindingException.class)
    public ResponseEntity<?> binding(Exception e) {
        return business(new ShopException(ShopError.INVALID));
    }

    @ExceptionHandler(ShopException.class)
    public ResponseEntity<?> business(ShopException e) {
        return ResponseEntity.status(e.error.status).body(Map.of("code", e.error.name(), "message", e.getMessage()));
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        org.springframework.web.method.annotation.HandlerMethodValidationException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<?> invalid(Exception e) {
        return business(new ShopException(ShopError.INVALID));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> failure(Exception e) {
        org.slf4j.LoggerFactory.getLogger(getClass())
                .error("Request failed; errorType={}", e.getClass().getSimpleName());
        return business(new ShopException(
                e.getClass().getName().startsWith("org.springframework.dao.")
                        ? ShopError.DATABASE_UNAVAILABLE
                        : ShopError.DEPENDENCY_UNAVAILABLE));
    }
}
