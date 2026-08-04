package ai.univs.gate.modules.feature.api.controller;

import ai.univs.gate.modules.feature.api.dto.face.LivenessRequestDTO;
import ai.univs.gate.modules.feature.api.dto.face.LivenessResponseDTO;
import ai.univs.gate.modules.feature.application.result.face.LivenessResult;
import ai.univs.gate.modules.feature.application.usecase.face.LivenessFaceUseCase;
import ai.univs.gate.shared.auth.UserContext;
import ai.univs.gate.support.message.MessageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * UG-274 반박 리뷰 후속.
 *
 * <p>{@code LivenessResult.prdioctionDesc} 는 <b>성공 시에도 "REAL"</b> 이 담긴다.
 * 이를 성공/실패 구분 없이 {@code MessageService.getFailureMessageOrEmpty} 로 넘기면,
 * messages_{ko,en}.properties 에 REAL 키가 없고 {@code setUseCodeAsDefaultMessage(true)} 라
 * 코드 문자열 "REAL" 이 그대로 failureReason 에 실려 나간다. 공개 문서는 성공 시 null 을 공표한다.
 *
 * <p>이 결함은 UG-274 이전에는 관측할 수 없었다 — 해당 엔드포인트가 apiKey 오전달로 항상 400 이라
 * 성공 응답 자체가 만들어지지 않았기 때문이다. 즉 엔드포인트를 살리는 커밋이 곧 이 결함을
 * 처음 노출시키는 커밋이다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UG-274: 얼굴 라이브니스 성공 응답에 failureReason 이 실리지 않아야 한다")
class LivenessFailureReasonTest {

    @Mock private LivenessFaceUseCase livenessFaceUseCase;
    @Mock private MessageService messageService;
    @InjectMocks private FaceController faceController;

    @BeforeEach
    void 컨텍스트_설정() {
        UserContext.set(UserContext.builder()
                .accountId("42")
                .apiKey("AK-UG274")
                .timezone("Asia/Seoul")
                .build());
    }

    @AfterEach
    void 컨텍스트_해제() {
        UserContext.clear();
    }

    @Test
    @DisplayName("성공 — prdioctionDesc 가 \"REAL\" 이어도 failureReason 은 비어 있다")
    void 성공() {
        given(livenessFaceUseCase.execute(any())).willReturn(result(true, "REAL"));

        LivenessResponseDTO body = call();

        assertEquals("", body.failureReason(),
                "성공인데 failureReason 이 채워졌다 — prdioctionDesc(\"REAL\")가 그대로 새어 나온다");
        verify(messageService, never()).getFailureMessageOrEmpty(any());
    }

    @Test
    @DisplayName("실패 — prdioctionDesc 는 i18n 메시지로 변환된다")
    void 실패() {
        given(livenessFaceUseCase.execute(any())).willReturn(result(false, "FAKE"));
        given(messageService.getFailureMessageOrEmpty("FAKE"))
                .willReturn("실제 얼굴 이미지로 확인되지 않습니다. 다시 시도해 주시기 바랍니다.");

        LivenessResponseDTO body = call();

        assertEquals("실제 얼굴 이미지로 확인되지 않습니다. 다시 시도해 주시기 바랍니다.", body.failureReason(),
                "실패 사유가 변환되지 않았다 — 성공 분기 처리가 실패까지 삼켰는지 확인할 것");
    }

    private LivenessResponseDTO call() {
        var response = faceController.liveness(new LivenessRequestDTO(null, "tx-1"));
        return Objects.requireNonNull(Objects.requireNonNull(response.getBody()).data());
    }

    private static LivenessResult result(boolean success, String prdioctionDesc) {
        return new LivenessResult(success, "0.99", 1, prdioctionDesc, "1.0", "0.5", "tx-1", true);
    }
}
