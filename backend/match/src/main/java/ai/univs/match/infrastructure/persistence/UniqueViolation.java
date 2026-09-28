package ai.univs.match.infrastructure.persistence;

import java.util.Locale;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 저장 실패가 <b>특정 유니크 제약</b> 위반인가 (UG-340).
 *
 * <p>{@link DataIntegrityViolationException} 은 NOT NULL·길이 초과 같은 다른 위반도 함께 담는다. 그것까지
 * "이미 등록됨" 으로 바꾸면 프로그래밍 오류가 클라이언트에게 멀쩡한 비즈니스 거절로 보인다. 그래서 제약
 * 이름으로 가린다.
 *
 * <p>이름은 두 곳에서 찾는다. Hibernate 가 뽑아 준 {@link ConstraintViolationException#getConstraintName()},
 * 그리고 드라이버 메시지. 방언마다 형태가 다르다 — PostgreSQL {@code "uk_descriptor_branch_face"}, Oracle
 * {@code (스키마.UK_DESCRIPTOR_BRANCH_FACE)} — 그래서 대소문자를 무시한 포함 비교를 쓴다.
 */
public final class UniqueViolation {

    public static final String DESCRIPTOR_BRANCH_FACE = "uk_descriptor_branch_face";
    public static final String BRANCH_NAME = "uk_branch_branch_name";

    private UniqueViolation() {
    }

    public static boolean of(DataIntegrityViolationException e, String constraintName) {
        String target = constraintName.toLowerCase(Locale.ROOT);
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException cve && mentions(cve.getConstraintName(), target)) {
                return true;
            }
            if (mentions(t.getMessage(), target)) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    private static boolean mentions(String text, String target) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(target);
    }
}
