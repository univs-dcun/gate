package ai.univs.match.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 제약 이름 판정 (UG-340). 드라이버 메시지 형태는 로컬 PostgreSQL 17·Oracle 23ai 컨테이너에서 실제로 받은
 * 문구를 옮겼다.
 */
@DisplayName("UG-340: 유니크 제약 위반 판정")
class UniqueViolationTest {

    private static DataIntegrityViolationException 감싼(Throwable driver) {
        return new DataIntegrityViolationException("could not execute statement", driver);
    }

    @Test
    @DisplayName("Hibernate 가 뽑은 제약 이름으로 판정한다")
    void hibernate_제약_이름() {
        var e = 감싼(new ConstraintViolationException("x", new SQLException("x"), "uk_descriptor_branch_face"));

        assertThat(UniqueViolation.of(e, UniqueViolation.DESCRIPTOR_BRANCH_FACE)).isTrue();
        assertThat(UniqueViolation.of(e, UniqueViolation.BRANCH_NAME)).isFalse();
    }

    @Test
    @DisplayName("PostgreSQL 메시지 — 제약 이름이 따옴표 안에 소문자로 온다")
    void postgresql_메시지() {
        var e = 감싼(new SQLException(
                "ERROR: duplicate key value violates unique constraint \"uk_branch_branch_name\"\n"
                        + "  Detail: Key (branch_name)=(b) already exists."));

        assertThat(UniqueViolation.of(e, UniqueViolation.BRANCH_NAME)).isTrue();
        assertThat(UniqueViolation.of(e, UniqueViolation.DESCRIPTOR_BRANCH_FACE)).isFalse();
    }

    @Test
    @DisplayName("Oracle 메시지 — 스키마가 붙고 대문자로 온다")
    void oracle_메시지() {
        var e = 감싼(new SQLIntegrityConstraintViolationException(
                "ORA-00001: unique constraint (UNIVS_MATCH.UK_DESCRIPTOR_BRANCH_FACE) violated on table "
                        + "UNIVS_MATCH.DESCRIPTOR columns (BRANCH_ID, FACE_ID)"));

        assertThat(UniqueViolation.of(e, UniqueViolation.DESCRIPTOR_BRANCH_FACE)).isTrue();
    }

    @Test
    @DisplayName("다른 제약·이름 없는 위반은 아니다")
    void 다른_위반() {
        assertThat(UniqueViolation.of(감싼(new SQLException("ORA-01400: cannot insert NULL into (\"X\".\"FACE_ID\")")),
                UniqueViolation.DESCRIPTOR_BRANCH_FACE)).isFalse();
        assertThat(UniqueViolation.of(new DataIntegrityViolationException("no cause"),
                UniqueViolation.DESCRIPTOR_BRANCH_FACE)).isFalse();
    }
}
