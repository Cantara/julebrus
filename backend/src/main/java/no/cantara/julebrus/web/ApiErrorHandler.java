package no.cantara.julebrus.web;

import no.cantara.julebrus.tasting.TastingRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> api(ApiException e) {
        return ResponseEntity.status(e.status()).body(new ApiError(e.code(), e.getMessage()));
    }

    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

    private static final ApiError CONFLICT = new ApiError("CONFLICT",
            "The request conflicts with the current state; reload and try again");

    /**
     * A unique constraint lost a race (double click, two tabs): the client sees a plain 409, not a 500.
     * Only unique violations (SQLState 23505) are translated; other integrity errors are bugs and stay 500s.
     */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> integrity(org.springframework.dao.DataIntegrityViolationException e) {
        if (!isUniqueViolation(e)) throw e;
        log.warn("Unique constraint conflict answered with 409: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(CONFLICT);
    }

    /** Two writers of one row (two admins, or a withdrawal during an approval): the loser gets 409. */
    @ExceptionHandler({org.springframework.orm.ObjectOptimisticLockingFailureException.class,
            org.springframework.dao.CannotAcquireLockException.class})
    public ResponseEntity<ApiError> lost(Exception e) {
        log.warn("Concurrent update answered with 409: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(CONFLICT);
    }

    static boolean isUniqueViolation(Throwable e) {
        if (e instanceof org.springframework.dao.DuplicateKeyException) return true;
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.sql.SQLException sql && "23505".equals(sql.getSQLState())) return true;
        }
        return false;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> status(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(new ApiError(HttpStatus.valueOf(e.getStatusCode().value()).name(), e.getReason()));
    }

    /** Malformed JSON, an unknown enum value, a path id or parameter of the wrong type: 400 with a code, not a bare Spring page. */
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> unreadable(Exception e) {
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION", "Invalid request"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage()).findFirst().orElse("Invalid request");
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION", msg));
    }
}
