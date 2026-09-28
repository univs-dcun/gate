package ai.univs.gate.shared.modality;

import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Set;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 꺼 둔 생체 인증 방식의 <b>동작</b>을 거절한다 (UG-223).
 *
 * <p><b>왜 필요한가.</b> 온프레미스 납품은 face 전용이다 — palm-service 가 없다. 그래도 gate 의
 * palm API 는 열려 있어서, 누가 palm 동작을 부르면 원격 호출이 실패하기 <b>전에</b> 흔적이 남는다.
 * 온프레미스 134 서버 실측(2026-09-28, On-prem):
 *
 * <ul>
 *   <li>등록·liveness — {@code PJ-005} 로 실패하고 이력에 실패 행이 1개씩 남는다
 *   <li>identify — 등록된 palm 이 없어 원격 호출 전에 끝나지만 역시 실패 행이 남는다
 *   <li>프로젝트 동의가 켜져 있으면 생체 이미지도 업로드된다 (등록·liveness·identify 모두 원격
 *       호출 전에 올린다)
 * </ul>
 *
 * <p>한 번 누르면 palm 대시보드에 건수가 잡히고 실패율이 100% 가 된다. 쓰지 않는 기능 때문에
 * 생체 이미지와 실패 기록이 쌓일 이유가 없다. 이 인터셉터는 컨트롤러에 닿기 전에 막으므로 업로드도
 * 이력도 생기지 않는다.
 *
 * <p><b>조회는 막지 않는다.</b> {@code GET}·{@code HEAD}·{@code OPTIONS} 는 통과시킨다. palm 조회는
 * palm-service 를 부르지 않고 gate DB 만 읽어 0/빈 값을 돌려준다. 온프레미스는 gate-web 의 palm
 * 화면(탭·차트·로그)이 빈 값을 보여 주는 것으로 충분하다고 결정했고 고객에게 그렇게 안내한다 —
 * 조회까지 막으면 그 화면들이 오류로 바뀐다.
 *
 * <p>이 구분이 성립하는 것은 modality 경로의 동작이 전부 {@code POST}·{@code DELETE} 이고 조회가
 * 전부 {@code GET} 이기 때문이다. {@code ModalityGateCoverageTest} 가 두 방향을 지킨다 — 동작
 * 엔드포인트가 차단 경로 밖에 생기면 실패하고, modality 경로에 <b>새 GET</b> 이 생기면 실패한다.
 * 다만 GET 이 정말 조회인지는 코드를 읽어야 알 수 있어서, 새 GET 은 사람이 확인하고 그 테스트의
 * 목록에 넣는다.
 *
 * <p>끄기 전에 그 방식의 특징점이 이미 있으면 조회는 보이는데 삭제는 거절된다. face 전용 납품에서는
 * palm 특징점이 생길 수 없어(등록이 palm-service 성공을 요구한다) 일어나지 않는다. 운영 중인 방식을
 * 끄게 되면 이 점을 먼저 판단할 것.
 *
 * <p><b>경로 패턴은 여기 한 곳에만 둔다.</b> {@code WebMvcConfig} 와 테스트가 같은 상수를 쓴다.
 */
public class ModalityGateInterceptor implements HandlerInterceptor {

    private static final List<String> FACE_PATHS =
            List.of("/api/v1/feature/face/**", "/api/v1/demo/feature/face/**");
    private static final List<String> PALM_PATHS =
            List.of("/api/v1/feature/palm/**", "/api/v1/demo/feature/palm/**");

    private static final Set<String> 조회_메서드 = Set.of("GET", "HEAD", "OPTIONS");

    private final FeatureType modality;
    private final ModalityProperties properties;

    public ModalityGateInterceptor(FeatureType modality, ModalityProperties properties) {
        this.modality = modality;
        this.properties = properties;
    }

    /** 그 방식의 API 가 사는 경로. {@code /**} 는 접두 경로 자체도 포함한다. */
    public static List<String> pathsOf(FeatureType modality) {
        return switch (modality) {
            case FACE -> FACE_PATHS;
            case PALM -> PALM_PATHS;
        };
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (properties.isEnabled(modality) || 조회_메서드.contains(request.getMethod())) {
            return true;
        }
        // GlobalExceptionHandler 가 받는다 — 인터셉터에서 던진 예외도 컨트롤러 어드바이스로 간다.
        throw new CustomGateException(ErrorType.FEATURE_NOT_ENABLED);
    }
}
