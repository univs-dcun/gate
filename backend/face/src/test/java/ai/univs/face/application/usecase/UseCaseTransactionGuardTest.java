package ai.univs.face.application.usecase;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 유스케이스에 트랜잭션 선언이 다시 붙지 않게 막는다 (UG-358 1·2단계).
 *
 * <p>붙으면 두 가지가 동시에 되돌아간다. 원격 호출(fxp·match) 동안 커넥션을 다시 쥐고, 그 안에서 부르는
 * {@code FaceHistoryRecorder} 가 바깥 트랜잭션에 합류해 「시작을 먼저 커밋한다」가 사라진다 — 롤백되면 이력 행이
 * 통째로 없어지던 예전 결함(5xx·타임아웃)이 돌아온다.
 *
 * <p>Spring·Jakarta 두 어노테이션을 모두 본다. 어느 쪽이든 Spring 이 트랜잭션을 연다.
 *
 * <p>1단계는 읽기 6개만 손으로 적었다. 2단계로 모든 유스케이스가 트랜잭션 없이 돌게 됐으므로 이제는 패키지를
 * <b>스캔</b>한다 — 새 유스케이스가 생겨도 따로 등록하지 않아도 막힌다. 스캔이 조용히 비지 않았는지는 알려진
 * 열두 개가 모두 잡히는지로 확인한다.
 */
@DisplayName("UG-358: 유스케이스는 트랜잭션을 선언하지 않는다")
class UseCaseTransactionGuardTest {

    /** 1단계(읽기)와 2단계(쓰기·추출·라이브니스)의 열두 개. 스캔이 이것을 모두 잡아야 한다. */
    private static final List<Class<?>> 알려진_유스케이스 = List.of(
            IdentifyUseCase.class,
            IdentifyByDescriptorUseCase.class,
            IdentifyCandidatesByDescriptorUseCase.class,
            VerifyByIdUseCase.class,
            VerifyByImageUseCase.class,
            VerifyByDescriptorUseCase.class,
            RegisterUseCase.class,
            RegisterByDescriptorUseCase.class,
            UpdateUseCase.class,
            DeleteUseCase.class,
            ExtractUseCase.class,
            LivenessUseCase.class);

    static List<Class<?>> 유스케이스() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));
        return scanner.findCandidateComponents(UseCaseTransactionGuardTest.class.getPackageName()).stream()
                .map(bean -> ClassUtils.resolveClassName(
                        Objects.requireNonNull(bean.getBeanClassName()), UseCaseTransactionGuardTest.class.getClassLoader()))
                .<Class<?>>map(type -> type)
                .toList();
    }

    @Test
    @DisplayName("스캔이 알려진 유스케이스 열두 개를 모두 잡는다")
    void 스캔이_모두_잡는다() {
        assertThat(유스케이스()).containsAll(알려진_유스케이스);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("유스케이스")
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
