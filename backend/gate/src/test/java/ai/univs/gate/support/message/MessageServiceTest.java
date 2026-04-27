package ai.univs.gate.support.message;

import ai.univs.gate.shared.web.enums.ErrorType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@DisplayName("MessageService.getMessage() 테스트")
class MessageServiceTest {

    // -------------------------------------------------------------------------
    // 단위 테스트 - MessageSource를 Mock으로 격리
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("[단위] Mockito로 MessageSource 격리")
    @ExtendWith(MockitoExtension.class)
    class UnitTest {

        @Mock
        private MessageSource messageSource;

        @InjectMocks
        private MessageService messageService;

        @BeforeEach
        void setKoreanLocale() {
            LocaleContextHolder.setLocale(Locale.KOREA);
        }

        @AfterEach
        void clearLocale() {
            LocaleContextHolder.resetLocaleContext();
        }

        // -- getMessage(ErrorType) --------------------------------------------

        @Test
        @DisplayName("ErrorType이 있으면 planType.name()을 키로 메시지를 반환한다")
        void getMessage_errorType_existingKey_returnsMessage() {
            // given
            ErrorType type = ErrorType.INVALID_INPUT;
            String expected = "유효하지 않은 값이 포함된 사용자의 요청입니다.";
            given(messageSource.getMessage(eq(type.name()), isNull(), any(Locale.class)))
                    .willReturn(expected);

            // when
            String result = messageService.getMessage(type);

            // then
            assertThat(result).isEqualTo(expected);
            then(messageSource).should()
                    .getMessage(eq("INVALID_INPUT"), isNull(), any(Locale.class));
        }

        @Test
        @DisplayName("ErrorType에 해당하는 메시지가 없으면 MessageSource가 반환하는 값을 그대로 반환한다")
        void getMessage_errorType_missingKey_returnsWhatMessageSourceReturns() {
            // given
            // setUseCodeAsDefaultMessage(true) 설정 시 MessageSource는 키 문자열 자체를 반환한다.
            ErrorType type = ErrorType.INTERNAL_SERVER_ERROR;
            given(messageSource.getMessage(eq(type.name()), isNull(), any(Locale.class)))
                    .willReturn("");   // 빈 문자열을 내려주는 시나리오

            // when
            String result = messageService.getMessage(type);

            // then
            assertThat(result).isEmpty();
        }

        // -- getMessage(String) -----------------------------------------------

        @Test
        @DisplayName("String 키가 있으면 메시지를 반환한다")
        void getMessage_string_existingKey_returnsMessage() {
            // given
            String key = "INVALID_INPUT";
            String expected = "유효하지 않은 값이 포함된 사용자의 요청입니다.";
            given(messageSource.getMessage(eq(key), isNull(), any(Locale.class)))
                    .willReturn(expected);

            // when
            String result = messageService.getMessage(key);

            // then
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("소문자 String을 넘기면 toUpperCase() 변환 후 조회한다")
        void getMessage_string_lowercaseInput_convertsToUppercaseBeforeLookup() {
            // given
            String lowercase = "invalid_input";
            String expected = "유효하지 않은 값이 포함된 사용자의 요청입니다.";
            given(messageSource.getMessage(eq("INVALID_INPUT"), isNull(), any(Locale.class)))
                    .willReturn(expected);

            // when
            String result = messageService.getMessage(lowercase);

            // then
            assertThat(result).isEqualTo(expected);
            // 대문자 키로 조회했는지 검증
            then(messageSource).should()
                    .getMessage(eq("INVALID_INPUT"), isNull(), any(Locale.class));
        }

        @Test
        @DisplayName("String 키가 없으면 MessageSource가 반환하는 값을 그대로 반환한다")
        void getMessage_string_missingKey_returnsWhatMessageSourceReturns() {
            // given
            // setUseCodeAsDefaultMessage(true) 설정 시 MessageSource는 키 문자열 자체를 반환한다.
            String unknownKey = "UNKNOWN_KEY";
            given(messageSource.getMessage(eq(unknownKey), isNull(), any(Locale.class)))
                    .willReturn("");   // 빈 문자열을 내려주는 시나리오

            // when
            String result = messageService.getMessage(unknownKey);

            // then
            assertThat(result).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // 통합 테스트 - 실제 messages_ko / messages_en 프로퍼티 파일 사용
    //   LocaleConfig의 setUseCodeAsDefaultMessage(true) 포함해서 동일하게 구성
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("[통합] 실제 MessageSource(messages_ko/en) 파일 사용")
    class IntegrationTest {

        private MessageService messageService;

        @BeforeEach
        void setUp() {
            var source = new ReloadableResourceBundleMessageSource();
            source.setBasename("classpath:messages");
            source.setDefaultEncoding("UTF-8");
            // LocaleConfig와 동일한 설정: 키 미존재 시 예외 대신 키 코드를 기본값으로 반환
            source.setUseCodeAsDefaultMessage(true);
            messageService = new MessageService(source);
        }

        @AfterEach
        void clearLocale() {
            LocaleContextHolder.resetLocaleContext();
        }

        // -- 한국어 로케일 -----------------------------------------------------

        @Test
        @DisplayName("[ko] ErrorType으로 한국어 메시지를 조회한다")
        void getMessage_errorType_korean_returnsKoreanMessage() {
            LocaleContextHolder.setLocale(Locale.KOREA);

            String result = messageService.getMessage(ErrorType.INVALID_INPUT);

            assertThat(result).isEqualTo("유효하지 않은 값이 포함된 사용자의 요청입니다.");
        }

        @Test
        @DisplayName("[ko] String 키(대문자)로 한국어 메시지를 조회한다")
        void getMessage_string_uppercase_korean_returnsKoreanMessage() {
            LocaleContextHolder.setLocale(Locale.KOREA);

            String result = messageService.getMessage("INVALID_INPUT");

            assertThat(result).isEqualTo("유효하지 않은 값이 포함된 사용자의 요청입니다.");
        }

        @Test
        @DisplayName("[ko] String 키(소문자)를 넘기면 대문자 변환 후 한국어 메시지를 조회한다")
        void getMessage_string_lowercase_korean_returnsKoreanMessage() {
            LocaleContextHolder.setLocale(Locale.KOREA);

            String result = messageService.getMessage("invalid_input");

            assertThat(result).isEqualTo("유효하지 않은 값이 포함된 사용자의 요청입니다.");
        }

        // -- 영어 로케일 -------------------------------------------------------

        @Test
        @DisplayName("[en] ErrorType으로 영어 메시지를 조회한다")
        void getMessage_errorType_english_returnsEnglishMessage() {
            LocaleContextHolder.setLocale(Locale.ENGLISH);

            String result = messageService.getMessage(ErrorType.INVALID_INPUT);

            assertThat(result).isEqualTo("The user's request contains invalid values.");
        }

        @Test
        @DisplayName("[en] String 키(소문자)를 넘기면 대문자 변환 후 영어 메시지를 조회한다")
        void getMessage_string_lowercase_english_returnsEnglishMessage() {
            LocaleContextHolder.setLocale(Locale.ENGLISH);

            String result = messageService.getMessage("invalid_input");

            assertThat(result).isEqualTo("The user's request contains invalid values.");
        }

        // -- 키 미존재 (setUseCodeAsDefaultMessage=true) -----------------------

        @Test
        @DisplayName("존재하지 않는 String 키 조회 시 키 코드 자체를 반환한다 (setUseCodeAsDefaultMessage=true)")
        void getMessage_string_unknownKey_returnsKeyCodeAsDefault() {
            LocaleContextHolder.setLocale(Locale.KOREA);

            // properties에 없는 키: setUseCodeAsDefaultMessage(true)에 의해 키 자체가 반환됨
            String result = messageService.getMessage("THIS_KEY_DOES_NOT_EXIST");

            assertThat(result).isEqualTo("THIS_KEY_DOES_NOT_EXIST");
        }

        @Test
        @DisplayName("존재하지 않는 키를 소문자로 넘기면 toUpperCase() 변환 후 키 코드 자체를 반환한다")
        void getMessage_string_unknownLowercaseKey_returnsUppercasedKeyCodeAsDefault() {
            LocaleContextHolder.setLocale(Locale.KOREA);

            String result = messageService.getMessage("this_key_does_not_exist");

            // toUpperCase() → "THIS_KEY_DOES_NOT_EXIST" → 프로퍼티 미존재 → 키 코드 그대로 반환
            assertThat(result).isEqualTo("THIS_KEY_DOES_NOT_EXIST");
        }
    }
}
