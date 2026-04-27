package ai.univs.match.shared.locale;

import ai.univs.match.shared.web.enums.ErrorType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MessageService")
class MessageServiceTest {

    @Nested
    @DisplayName("단위 테스트 - MessageSource 호출 방식 검증")
    @ExtendWith(MockitoExtension.class)
    class UnitTest {

        @Mock
        private MessageSource messageSource;

        @InjectMocks
        private MessageService messageService;

        @AfterEach
        void resetLocale() {
            LocaleContextHolder.resetLocaleContext();
        }

        @Nested
        @DisplayName("getMessage(ErrorType)")
        class GetMessageByErrorType {

            @Test
            @DisplayName("ErrorType의 name()을 키로 사용하여 MessageSource를 조회한다")
            void whenCalled_thenUsesErrorTypeNameAsKey() {
                ErrorType errorType = ErrorType.UNAUTHORIZED;
                when(messageSource.getMessage(eq("UNAUTHORIZED"), isNull(), any(Locale.class)))
                        .thenReturn("인증 없이 요청을 시도했습니다.");

                messageService.getMessage(errorType);

                verify(messageSource).getMessage(eq("UNAUTHORIZED"), isNull(), any(Locale.class));
            }

            @Test
            @DisplayName("MessageSource에서 반환된 메시지를 그대로 반환한다")
            void whenMessageSourceReturns_thenReturnSameMessage() {
                String expected = "인증 없이 요청을 시도했습니다.";
                when(messageSource.getMessage(eq("UNAUTHORIZED"), isNull(), any(Locale.class)))
                        .thenReturn(expected);

                String result = messageService.getMessage(ErrorType.UNAUTHORIZED);

                assertThat(result).isEqualTo(expected);
            }

            @Test
            @DisplayName("LocaleContextHolder의 현재 Locale을 MessageSource에 전달한다")
            void whenLocaleIsSet_thenPassesCurrentLocaleToMessageSource() {
                LocaleContextHolder.setLocale(Locale.ENGLISH);
                when(messageSource.getMessage(any(String.class), isNull(), eq(Locale.ENGLISH)))
                        .thenReturn("any message");

                messageService.getMessage(ErrorType.UNAUTHORIZED);

                verify(messageSource).getMessage(eq("UNAUTHORIZED"), isNull(), eq(Locale.ENGLISH));
            }
        }

        @Nested
        @DisplayName("getMessage(String)")
        class GetMessageByString {

            @Test
            @DisplayName("입력 문자열을 대문자로 변환하여 MessageSource를 조회한다")
            void whenCalled_thenConvertsToUpperCase() {
                when(messageSource.getMessage(eq("UNAUTHORIZED"), isNull(), any(Locale.class)))
                        .thenReturn("인증 없이 요청을 시도했습니다.");

                messageService.getMessage("unauthorized");

                verify(messageSource).getMessage(eq("UNAUTHORIZED"), isNull(), any(Locale.class));
            }

            @Test
            @DisplayName("소문자 입력도 대문자로 변환하여 올바른 메시지를 조회한다")
            void whenLowerCaseInput_thenConvertsToUpperCaseForLookup() {
                String expected = "인증 없이 요청을 시도했습니다.";
                when(messageSource.getMessage(eq("UNAUTHORIZED"), isNull(), any(Locale.class)))
                        .thenReturn(expected);

                String result = messageService.getMessage("unauthorized");

                assertThat(result).isEqualTo(expected);
            }

            @Test
            @DisplayName("혼합 대소문자 입력도 대문자로 변환하여 조회한다")
            void whenMixedCaseInput_thenConvertsToUpperCaseForLookup() {
                when(messageSource.getMessage(eq("INTERNAL_SERVER_ERROR"), isNull(), any(Locale.class)))
                        .thenReturn("유니버스 플랫폼에서 오류가 발생하였습니다.");

                messageService.getMessage("Internal_Server_Error");

                verify(messageSource).getMessage(eq("INTERNAL_SERVER_ERROR"), isNull(), any(Locale.class));
            }

            @Test
            @DisplayName("이미 대문자인 입력은 그대로 사용하여 조회한다")
            void whenUpperCaseInput_thenUsesAsIs() {
                String expected = "인증 없이 요청을 시도했습니다.";
                when(messageSource.getMessage(eq("UNAUTHORIZED"), isNull(), any(Locale.class)))
                        .thenReturn(expected);

                String result = messageService.getMessage("UNAUTHORIZED");

                assertThat(result).isEqualTo(expected);
            }

            @Test
            @DisplayName("LocaleContextHolder의 현재 Locale을 MessageSource에 전달한다")
            void whenLocaleIsSet_thenPassesCurrentLocaleToMessageSource() {
                LocaleContextHolder.setLocale(Locale.ENGLISH);
                when(messageSource.getMessage(any(String.class), isNull(), eq(Locale.ENGLISH)))
                        .thenReturn("any message");

                messageService.getMessage("unauthorized");

                verify(messageSource).getMessage(eq("UNAUTHORIZED"), isNull(), eq(Locale.ENGLISH));
            }
        }
    }

    @Nested
    @DisplayName("통합 테스트 - 실제 properties 파일 기반 메시지 조회")
    class IntegrationTest {

        private MessageService messageService;

        @BeforeEach
        void setUp() {
            var messageSource = new ReloadableResourceBundleMessageSource();
            messageSource.setBasename("classpath:messages");
            messageSource.setDefaultEncoding("UTF-8");
            messageSource.setUseCodeAsDefaultMessage(true);
            messageService = new MessageService(messageSource);
        }

        @AfterEach
        void resetLocale() {
            LocaleContextHolder.resetLocaleContext();
        }

        @Nested
        @DisplayName("getMessage(ErrorType) - 로케일별 메시지 조회")
        class GetMessageByErrorTypeWithRealSource {

            @Test
            @DisplayName("한국어 로케일에서 한국어 메시지를 반환한다")
            void whenKoreanLocale_thenReturnKoreanMessage() {
                LocaleContextHolder.setLocale(Locale.KOREA);

                String result = messageService.getMessage(ErrorType.UNAUTHORIZED);

                assertThat(result).isEqualTo("인증 없이 요청을 시도했습니다.");
            }

            @Test
            @DisplayName("영어 로케일에서 영어 메시지를 반환한다")
            void whenEnglishLocale_thenReturnEnglishMessage() {
                LocaleContextHolder.setLocale(Locale.ENGLISH);

                String result = messageService.getMessage(ErrorType.UNAUTHORIZED);

                assertThat(result).isEqualTo("A request was attempted without authentication.");
            }

            @Test
            @DisplayName("INTERNAL_SERVER_ERROR - 한국어 메시지를 반환한다")
            void whenInternalServerError_andKoreanLocale_thenReturnKoreanMessage() {
                LocaleContextHolder.setLocale(Locale.KOREA);

                String result = messageService.getMessage(ErrorType.INTERNAL_SERVER_ERROR);

                assertThat(result).isEqualTo("유니버스 플랫폼에서 오류가 발생하였습니다.");
            }

            @Test
            @DisplayName("INTERNAL_SERVER_ERROR - 영어 메시지를 반환한다")
            void whenInternalServerError_andEnglishLocale_thenReturnEnglishMessage() {
                LocaleContextHolder.setLocale(Locale.ENGLISH);

                String result = messageService.getMessage(ErrorType.INTERNAL_SERVER_ERROR);

                assertThat(result).isEqualTo("There has been an error with the Universe platform.");
            }

            @Test
            @DisplayName("NOT_SUPPORTED_VERSION - 한국어 메시지를 반환한다")
            void whenNotSupportedVersion_andKoreanLocale_thenReturnKoreanMessage() {
                LocaleContextHolder.setLocale(Locale.KOREA);

                String result = messageService.getMessage(ErrorType.NOT_SUPPORTED_VERSION);

                assertThat(result).isEqualTo("이미지에서 사용자 얼굴이 확인되지 않았습니다. 다른 이미지 파일로 다시 요청해주시기 바랍니다.");
            }

            @Test
            @DisplayName("NOT_SUPPORTED_VERSION - 영어 메시지를 반환한다")
            void whenNotSupportedVersion_andEnglishLocale_thenReturnEnglishMessage() {
                LocaleContextHolder.setLocale(Locale.ENGLISH);

                String result = messageService.getMessage(ErrorType.NOT_SUPPORTED_VERSION);

                assertThat(result).isEqualTo("No face was detected in the image. Please try again with a different image file.");
            }

            @Test
            @DisplayName("로케일을 변경하면 다른 언어의 메시지가 반환된다")
            void whenLocaleChanges_thenReturnDifferentLanguageMessage() {
                LocaleContextHolder.setLocale(Locale.KOREA);
                String koreanMessage = messageService.getMessage(ErrorType.UNAUTHORIZED);

                LocaleContextHolder.setLocale(Locale.ENGLISH);
                String englishMessage = messageService.getMessage(ErrorType.UNAUTHORIZED);

                assertThat(koreanMessage).isNotEqualTo(englishMessage);
            }
        }

        @Nested
        @DisplayName("getMessage(String) - 로케일별 메시지 조회")
        class GetMessageByStringWithRealSource {

            @Test
            @DisplayName("대문자 ErrorType 이름으로 한국어 메시지를 반환한다")
            void whenUpperCaseKey_andKoreanLocale_thenReturnKoreanMessage() {
                LocaleContextHolder.setLocale(Locale.KOREA);

                String result = messageService.getMessage("UNAUTHORIZED");

                assertThat(result).isEqualTo("인증 없이 요청을 시도했습니다.");
            }

            @Test
            @DisplayName("소문자 입력도 대문자로 변환하여 한국어 메시지를 반환한다")
            void whenLowerCaseKey_andKoreanLocale_thenReturnKoreanMessage() {
                LocaleContextHolder.setLocale(Locale.KOREA);

                String result = messageService.getMessage("unauthorized");

                assertThat(result).isEqualTo("인증 없이 요청을 시도했습니다.");
            }

            @Test
            @DisplayName("소문자 입력도 대문자로 변환하여 영어 메시지를 반환한다")
            void whenLowerCaseKey_andEnglishLocale_thenReturnEnglishMessage() {
                LocaleContextHolder.setLocale(Locale.ENGLISH);

                String result = messageService.getMessage("unauthorized");

                assertThat(result).isEqualTo("A request was attempted without authentication.");
            }

            @Test
            @DisplayName("존재하지 않는 키는 MessageSource 설정에 따라 코드 자체를 반환한다")
            void whenUnknownKey_thenReturnCodeAsDefaultMessage() {
                LocaleContextHolder.setLocale(Locale.KOREA);

                // useCodeAsDefaultMessage=true 이므로 키를 그대로 반환
                String result = messageService.getMessage("UNKNOWN_KEY");

                assertThat(result).isEqualTo("UNKNOWN_KEY");
            }
        }
    }
}
