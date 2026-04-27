package ai.univs.match.application.service;

import ai.univs.match.application.input.DescriptorDetail;
import ai.univs.match.application.result.IdentifyResult;
import ai.univs.match.domain.entity.Branch;
import ai.univs.match.shared.exception.CustomFaceMatcherException;
import ai.univs.match.shared.web.enums.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@DisplayName("DuplicateService")
@ExtendWith(MockitoExtension.class)
class DuplicateServiceTest {

    @Mock
    private IdentifyService identifyService;

    @InjectMocks
    private DuplicateService duplicateService;

    private Branch branch;
    private DescriptorDetail descriptorDetail;

    @BeforeEach
    void setUp() {
        branch = Branch.builder()
                .id(1L)
                .branchName("testBranch")
                .build();

        descriptorDetail = DescriptorDetail.from(createBase64Descriptor(59));
    }

    @Nested
    @DisplayName("checkDuplicateDescriptor")
    class CheckDuplicateDescriptor {

        @Nested
        @DisplayName("신규 등록(isUpdate=false) 상황")
        class WhenRegister {

            @Test
            @DisplayName("유사도가 0.85 미만이면 예외를 던지지 않는다")
            void whenSimilarityBelow085_thenNoException() {
                when(identifyService.identify(branch, descriptorDetail))
                        .thenReturn(new IdentifyResult("other-face", "0.84999"));

                assertThatCode(() ->
                        duplicateService.checkDuplicateDescriptor(branch, descriptorDetail, "new-face", false))
                        .doesNotThrowAnyException();
            }

            @Test
            @DisplayName("유사도가 정확히 0.85이면 ALREADY_REGISTERED_DESCRIPTOR 예외를 던진다")
            void whenSimilarityExactly085_thenThrowException() {
                when(identifyService.identify(branch, descriptorDetail))
                        .thenReturn(new IdentifyResult("other-face", "0.85000"));

                assertThatThrownBy(() ->
                        duplicateService.checkDuplicateDescriptor(branch, descriptorDetail, "new-face", false))
                        .isInstanceOf(CustomFaceMatcherException.class)
                        .satisfies(ex -> assertThat085Threshold((CustomFaceMatcherException) ex));
            }

            @Test
            @DisplayName("유사도가 0.85 이상이면 ALREADY_REGISTERED_DESCRIPTOR 예외를 던진다")
            void whenSimilarityAbove085_thenThrowException() {
                when(identifyService.identify(branch, descriptorDetail))
                        .thenReturn(new IdentifyResult("other-face", "0.95000"));

                assertThatThrownBy(() ->
                        duplicateService.checkDuplicateDescriptor(branch, descriptorDetail, "new-face", false))
                        .isInstanceOf(CustomFaceMatcherException.class)
                        .satisfies(ex -> assertThat085Threshold((CustomFaceMatcherException) ex));
            }
        }

        @Nested
        @DisplayName("수정(isUpdate=true) 상황")
        class WhenUpdate {

            @Test
            @DisplayName("유사도가 0.85 미만이면 예외를 던지지 않는다")
            void whenSimilarityBelow085_thenNoException() {
                when(identifyService.identify(branch, descriptorDetail))
                        .thenReturn(new IdentifyResult("other-face", "0.84999"));

                assertThatCode(() ->
                        duplicateService.checkDuplicateDescriptor(branch, descriptorDetail, "my-face", true))
                        .doesNotThrowAnyException();
            }

            @Test
            @DisplayName("유사도가 0.85 이상이고 매칭된 faceId가 동일하면 본인이므로 예외를 던지지 않는다")
            void whenSimilarityAbove085AndSameFaceId_thenNoException() {
                when(identifyService.identify(branch, descriptorDetail))
                        .thenReturn(new IdentifyResult("my-face", "0.95000"));

                assertThatCode(() ->
                        duplicateService.checkDuplicateDescriptor(branch, descriptorDetail, "my-face", true))
                        .doesNotThrowAnyException();
            }

            @Test
            @DisplayName("유사도가 0.85 이상이고 매칭된 faceId가 다르면 ALREADY_REGISTERED_DESCRIPTOR 예외를 던진다")
            void whenSimilarityAbove085AndDifferentFaceId_thenThrowException() {
                when(identifyService.identify(branch, descriptorDetail))
                        .thenReturn(new IdentifyResult("other-face", "0.95000"));

                assertThatThrownBy(() ->
                        duplicateService.checkDuplicateDescriptor(branch, descriptorDetail, "my-face", true))
                        .isInstanceOf(CustomFaceMatcherException.class)
                        .satisfies(ex -> assertThat085Threshold((CustomFaceMatcherException) ex));
            }

            @Test
            @DisplayName("유사도가 정확히 0.85이고 매칭된 faceId가 동일하면 예외를 던지지 않는다")
            void whenSimilarityExactly085AndSameFaceId_thenNoException() {
                when(identifyService.identify(branch, descriptorDetail))
                        .thenReturn(new IdentifyResult("my-face", "0.85000"));

                assertThatCode(() ->
                        duplicateService.checkDuplicateDescriptor(branch, descriptorDetail, "my-face", true))
                        .doesNotThrowAnyException();
            }
        }
    }

    private void assertThat085Threshold(CustomFaceMatcherException ex) {
        org.assertj.core.api.Assertions.assertThat(ex.getErrorType())
                .isEqualTo(ErrorType.ALREADY_REGISTERED_DESCRIPTOR);
    }

    static String createBase64Descriptor(int version) {
        byte[] bytes = new byte[520];
        bytes[4] = (byte) version;
        return Base64.getEncoder().encodeToString(bytes);
    }
}
