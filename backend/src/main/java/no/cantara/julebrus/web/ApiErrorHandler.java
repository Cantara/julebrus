package no.cantara.julebrus.web;

import no.cantara.julebrus.tasting.TastingRules;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrorHandler {

    public record ApiError(String code, String message) {
    }

    /** Rule violations are 422 with a stable code the frontend can switch on. */
    @ExceptionHandler(TastingRules.RuleViolation.class)
    public ResponseEntity<ApiError> ruleViolation(TastingRules.RuleViolation e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiError(e.violation().name(), e.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> status(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(new ApiError(HttpStatus.valueOf(e.getStatusCode().value()).name(), e.getReason()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage()).findFirst().orElse("Invalid request");
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION", msg));
    }
}
