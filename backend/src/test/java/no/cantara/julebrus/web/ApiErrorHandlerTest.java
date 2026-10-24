package no.cantara.julebrus.web;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Only unique violations become 409 CONFLICT; every other integrity error stays a server error. */
class ApiErrorHandlerTest {

    private final ApiErrorHandler handler = new ApiErrorHandler();

    @Test
    void uniqueViolationsAre409Conflict() {
        var viaSqlState = new DataIntegrityViolationException("dup", new SQLException("duplicate key", "23505"));
        var res = handler.integrity(viaSqlState);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody().code()).isEqualTo("CONFLICT");
        assertThat(handler.integrity(new DuplicateKeyException("dup")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void otherIntegrityErrorsAreNotHiddenAsConflicts() {
        var foreignKey = new DataIntegrityViolationException("fk", new SQLException("fk violation", "23503"));
        assertThatThrownBy(() -> handler.integrity(foreignKey)).isSameAs(foreignKey);
    }

    @Test
    void lostUpdatesAre409Conflict() {
        var res = handler.lost(new org.springframework.orm.ObjectOptimisticLockingFailureException(Object.class, "x"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody().code()).isEqualTo("CONFLICT");
    }
}
