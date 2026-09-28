package ai.univs.palm.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import ai.univs.palm.application.input.RegisterInput;
import ai.univs.palm.application.result.RegisterResult;
import ai.univs.palm.domain.repository.PalmHistoryRepository;
import ai.univs.palm.infrastructure.feign.PalmFeign;
import ai.univs.palm.infrastructure.feign.dto.RegisterFeignRequestDTO;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 등록이 호출자가 발급한 palmId 를 SmartFace 에 그대로 쓰는가 (UG-337).
 *
 * <p>이 유스케이스에는 테스트가 없었다. 여기서는 UG-337 이 바꾼 한 가지 — 어떤 id 로 등록하는가 —
 * 만 고정한다. 중복 검사(identify)는 결과 없음으로, 라이브니스는 끈 채로 둔다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UG-337: 팜 등록의 palmId")
class RegisterUseCaseTest {

    private static final String UUID_형식 =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    @Mock private PalmFeign palmFeign;
    @Mock private PalmHistoryRepository palmHistoryRepository;
    @Mock private ai.univs.palm.infrastructure.repository.PalmLivenessJpaRepository palmLivenessJpaRepository;

    @InjectMocks private RegisterUseCase useCase;

    private static RegisterInput 입력(String palmId) {
        return new RegisterInput(palmId,
                new MockMultipartFile("palmImage", "p.jpg", "image/jpeg", new byte[]{1, 2, 3}),
                "branch-A", "txn-1", "client", false);
    }

    private RegisterFeignRequestDTO 보낸_요청() {
        ArgumentCaptor<RegisterFeignRequestDTO> req = ArgumentCaptor.forClass(RegisterFeignRequestDTO.class);
        verify(palmFeign).register(req.capture());
        return req.getValue();
    }

    /**
     * <b>호출자 id 를 그대로 쓴다.</b> 새로 발급하면 gate 가 이력에 남긴 id 가 가리키는 것이 없어져,
     * gate 쓰기가 실패했을 때 되돌릴 대상을 찾지 못한다(UG-338).
     */
    @Test
    @DisplayName("palmId 를 주면 그 값으로 SmartFace 에 등록하고 그대로 돌려준다")
    void 호출자_id_로_등록() {
        String 발급 = "0f8fad5b-d9cb-469f-a165-70867728950e";
        given(palmFeign.identify(any())).willReturn(List.of());

        RegisterResult result = useCase.execute(입력(발급));

        assertThat(보낸_요청().getId()).isEqualTo(발급);
        assertThat(result.palmId()).isEqualTo(발급);
    }

    @Test
    @DisplayName("palmId 가 비어 있으면 이 서버가 UUID 를 발급한다 — 지금까지와 같다")
    void 없으면_발급() {
        given(palmFeign.identify(any())).willReturn(List.of());

        RegisterResult result = useCase.execute(입력(""));

        String id = 보낸_요청().getId();
        assertThat(id).matches(UUID_형식);
        assertThat(result.palmId()).isEqualTo(id);
    }
}
