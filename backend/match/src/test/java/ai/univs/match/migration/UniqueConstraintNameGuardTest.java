package ai.univs.match.migration;

import static org.assertj.core.api.Assertions.assertThat;

import ai.univs.match.infrastructure.persistence.UniqueViolation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.DisplayName;

/**
 * 마이그레이션의 제약 이름과 {@link UniqueViolation} 상수가 같은가 (UG-340 반박 리뷰).
 *
 * <p>둘이 어긋나면 위반을 알아보지 못해 동시 등록이 {@code MATCH-003} 대신 조용히 500 으로 떨어진다. 체크섬 가드는
 * 새 파일이면 줄을 추가하는 것이 정상 절차라 이 경우를 잡지 못한다 — 리뷰가 oracle V4 의 이름만 바꾸고 체크섬을
 * 갱신한 변이로 녹색을 확인했다.
 */
@DisplayName("UG-340: 유니크 제약 이름 = UniqueViolation 상수")
class UniqueConstraintNameGuardTest {

    private static final Path MIGRATION = Path.of("src/main/resources/db/migration");

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "postgresql/V4__unique_descriptor_branch_face.sql, DESCRIPTOR_BRANCH_FACE",
            "oracle/V4__unique_descriptor_branch_face.sql, DESCRIPTOR_BRANCH_FACE",
            "postgresql/V5__unique_branch_name.sql, BRANCH_NAME",
            "oracle/V5__unique_branch_name.sql, BRANCH_NAME",
    })
    void 제약_이름이_같다(String file, String constant) throws IOException {
        String expected = constant.equals("BRANCH_NAME") ? UniqueViolation.BRANCH_NAME : UniqueViolation.DESCRIPTOR_BRANCH_FACE;
        String sql = Files.readString(MIGRATION.resolve(file)).toLowerCase(Locale.ROOT);

        assertThat(sql).contains("add constraint " + expected.toLowerCase(Locale.ROOT) + " unique");
    }
}
