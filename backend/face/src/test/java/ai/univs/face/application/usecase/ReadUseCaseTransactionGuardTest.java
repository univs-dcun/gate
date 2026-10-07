package ai.univs.face.application.usecase;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 읽기 유스케이스에 트랜잭션 선언이 다시 붙지 않게 막는다 (UG-358 1단계).
 *
 * <p>붙으면 두 가지가 동시에 되돌아간다. 원격 호출(fxp·match) 동안 커넥션을 다시 쥐고, 그 안에서 부르는
 * {@code FaceHistoryRecorder} 가 바깥 트랜잭션에 합류해 「시작을 먼저 커밋한다」가 사라진다 — 롤백되면 이력 행이
 * 통째로 없어지던 예전 결함(5xx·타임아웃)이 돌아온다.
 *
 * <p>Spring·Jakarta 두 어노테이션을 모두 본다. 어느 쪽이든 Spring 이 트랜잭션을 연다.
 */
@DisplayName("UG-358: 읽기 유스케이스는 트랜잭션을 선언하지 않는다")
class ReadUseCaseTransactionGuardTest {

    static List<Class<?>> 읽기_유스케이스() {
        return List.of(
                IdentifyUseCase.class,
                IdentifyByDescriptorUseCase.class,
                IdentifyCandidatesByDescriptorUseCase.class,
                VerifyByIdUseCase.class,
                VerifyByImageUseCase.class,
                VerifyByDescriptorUseCase.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("읽기_유스케이스")
    void 클래스와_메서드에_트랜잭션_선언이_없다(Class<?> useCase) {
        assertThat(트랜잭션_선언(useCase.getAnnotations())).as(useCase.getSimpleName()).isFalse();
        for (Method method : useCase.getDeclaredMethods()) {
            assertThat(트랜잭션_선언(method.getAnnotations()))
                    .as(useCase.getSimpleName() + "#" + method.getName())
                    .isFalse();
        }
    }

    private static boolean 트랜잭션_선언(java.lang.annotation.Annotation[] annotations) {
        for (var annotation : annotations) {
            Class<?> type = annotation.annotationType();
            if (type == org.springframework.transaction.annotation.Transactional.class
                    || type == jakarta.transaction.Transactional.class) {
                return true;
            }
        }
        return false;
    }
}
