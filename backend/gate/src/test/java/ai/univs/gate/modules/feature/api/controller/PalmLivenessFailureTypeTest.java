package ai.univs.gate.modules.feature.api.controller;

import ai.univs.gate.modules.feature.api.dto.palm.PalmLivenessRequestDTO;
import ai.univs.gate.modules.feature.api.dto.palm.PalmLivenessResponseDTO;
import ai.univs.gate.modules.feature.application.result.palm.PalmLivenessResult;
import ai.univs.gate.modules.feature.application.usecase.palm.LivenessPalmUseCase;
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
 * UG-393: 손바닥 라이브니스 응답에도 얼굴(UG-391)과 같은 {@code failureType} 이 실린다.
 * 화면이 사유 문장이 아니라 타입으로 촬영 문제와 위변조를 가를 수 있어야 한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UG-393: 손바닥 라이브니스 응답의 failureType")
class PalmLivenessFailureTypeTest {

    @Mock private LivenessPalmUseCase livenessPalmUseCase;
    @Mock private MessageService messageService;
    @InjectMocks private PalmController palmController;

    @BeforeEach
    void 컨텍스트_설정() {
        UserContext.set(UserContext.builder()
                .accountId("42")
                .apiKey("AK-UG393")
                .timezone("Asia/Seoul")
                .build());
    }

    @AfterEach
    void 컨텍스트_해제() {
        UserContext.clear();
    }

    @Test
    @DisplayName("성공 — failureType · failureReason 모두 비어 있다")
    void 성공() {
        given(livenessPalmUseCase.execute(any())).willReturn(result(true, "live"));

        PalmLivenessResponseDTO body = call();

        assertEquals(true, body.success());
        assertEquals("", body.failureType());
        assertEquals("", body.failureReason());
        verify(messageService, never()).getLivenessFailureMessage(any());
    }

    @Test
    @DisplayName("실패 — failureType 은 엔진 사유 대문자, failureReason 은 서버 문구")
    void 실패() {
        given(livenessPalmUseCase.execute(any())).willReturn(result(false, "spoof"));
        given(messageService.getLivenessFailureMessage("SPOOF")).willReturn("문구");

        PalmLivenessResponseDTO body = call();

        assertEquals(false, body.success());
        assertEquals("SPOOF", body.failureType());
        assertEquals("문구", body.failureReason());
    }

    @Test
    @DisplayName("위조 판정(사유 없음) — failureType 은 LIVENESS_FAILED, failureReason 은 그 문구 (예전에는 빈 값)")
    void 사유없는_실패() {
        given(livenessPalmUseCase.execute(any())).willReturn(result(false, null));
        given(messageService.getLivenessFailureMessage("LIVENESS_FAILED")).willReturn("라이브니스 검증에 실패하였습니다.");

        PalmLivenessResponseDTO body = call();

        assertEquals("LIVENESS_FAILED", body.failureType());
        assertEquals("라이브니스 검증에 실패하였습니다.", body.failureReason());
    }

    @Test
    @DisplayName("엔진 오류 문장 — 공백이 _ 인 대문자 식별자")
    void 엔진_오류_문장() {
        given(livenessPalmUseCase.execute(any())).willReturn(result(false, " No palm  detected "));
        given(messageService.getLivenessFailureMessage("NO_PALM_DETECTED")).willReturn("문구");

        PalmLivenessResponseDTO body = call();

        assertEquals("NO_PALM_DETECTED", body.failureType());
        assertEquals("문구", body.failureReason());
    }

    private PalmLivenessResponseDTO call() {
        var response = palmController.liveness(new PalmLivenessRequestDTO(null, "tx-1"));
        return Objects.requireNonNull(Objects.requireNonNull(response.getBody()).data());
    }

    private static PalmLivenessResult result(boolean success, String message) {
        return new PalmLivenessResult(success, 50.0, 80.0, message, "tx-1");
    }
}
