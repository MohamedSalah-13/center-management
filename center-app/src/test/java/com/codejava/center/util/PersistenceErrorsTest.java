package com.codejava.center.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PersistenceErrorsTest {

    @Test
    void findsConstraintNameAnywhereInCauseChainIgnoringCase() {
        RuntimeException error = new RuntimeException("outer",
                new IllegalStateException("Duplicate entry for key 'UK_ATTENDANCE_STUDENT_SESSION'"));

        assertThat(PersistenceErrors.isConstraint(error, "uk_attendance_student_session")).isTrue();
    }

    @Test
    void doesNotMisclassifyAnotherDatabaseFailure() {
        RuntimeException error = new RuntimeException("connection closed");

        assertThat(PersistenceErrors.isConstraint(error, "uk_attendance_student_session")).isFalse();
    }
}
