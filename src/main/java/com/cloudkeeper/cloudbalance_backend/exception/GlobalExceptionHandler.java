package com.cloudkeeper.cloudbalance_backend.exception;

import com.cloudkeeper.cloudbalance_backend.dto.response.ApiResponse;
import com.cloudkeeper.cloudbalance_backend.logging.Logger;
import com.cloudkeeper.cloudbalance_backend.logging.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 404 Not Found -> Entity not found
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        logger.error("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.<Void>builder().success(false).error(ex.getMessage()).build());
    }

    // 401 Unauthorized -> Invalid login
    @ExceptionHandler({InvalidCredentialsException.class, BadCredentialsException.class})
    public ResponseEntity<ApiResponse<Void>> handleInvalidCredentials(Exception ex) {
        logger.error("Authentication failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(  // Changed to 400
                ApiResponse.<Void>builder().success(false).error("Invalid email or password").build());
    }

    // 403 Forbidden -> Insufficient Permissions
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        logger.error("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.<Void>builder().success(false).error("Access denied").build());
    }

    // 400 Bad request
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        logger.error("Invalid argument: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.<Void>builder().success(false).error(ex.getMessage()).build());
    }

    // 400 Bad Request
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.<Map<String, String>>builder().success(false).error("Validation failed").data(errors).build());
    }

    // 409 Conflict -> Duplicate entity
    @ExceptionHandler(ResourceAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceExists(ResourceAlreadyExistsException ex) {
        logger.error("Duplicate Entity : ", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.<Void>builder().success(false).error("A resource already exists.").build());
    }

    // Handle MaxSessionsReachedException
    @ExceptionHandler(MaxSessionsReachedException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleMaxSessionsReached(MaxSessionsReachedException ex) {

        logger.warn("Max sessions reached: {}", ex.getMessage());

        Map<String, Object> data = new HashMap<>();
        data.put("hasActiveSession", true);
        data.put("reason", "MAX_SESSIONS_REACHED");
        data.put("suggestion", "Please logout from another device or use force-login");

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiResponse.<Map<String, Object>>builder().success(false).message(ex.getMessage()).data(data).build());
    }

    // Handle Redis/Session errors gracefully
    @ExceptionHandler({org.springframework.data.redis.RedisConnectionFailureException.class, org.springframework.data.redis.serializer.SerializationException.class})
    public ResponseEntity<ApiResponse<Void>> handleRedisErrors(Exception ex) {

        logger.error("Redis error: {}", ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.<Void>builder().success(false).message("Session service temporarily unavailable. Please try again.").build());
    }

    // 500 Internal Server Error -> Unexpected errors
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGlobalException(Exception ex) {
        logger.error("Unexpected error: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.<Void>builder().success(false).error("An unexpected error occurred").build());
    }

    @ExceptionHandler(TokenRefreshException.class)
    public ResponseEntity<ApiResponse<Void>> handleTokenRefreshException(TokenRefreshException ex) {
        logger.error("Token refresh error : {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.<Void>builder().success(false).error(ex.getMessage()).build());
    }

}
