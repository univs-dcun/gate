package ai.univs.gate.support.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;

import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;

/** UG-326: 실패 사유 번역이 없어도 목록이 죽지 않는다. */
@ExtendWith(MockitoExtension.class)
@DisplayName("MessageService.getFailureMessageOrEmpty")
class MessageServiceTest {

    @Mock private MessageSource messageSource;
    @InjectMocks private MessageService messageService;

    @Test
    @DisplayName("등록된 코드는 번역을, 빈 값은 빈 문자열을 돌려준다")
    void 정상() {
        given(messageSource.getMessage(eq("NOT_MATCH"), isNull(), any(Locale.class))).willReturn("일치하는 사용자가 없습니다.");
        assertThat(messageService.getFailureMessageOrEmpty("NOT_MATCH")).isEqualTo("일치하는 사용자가 없습니다.");
        assertThat(messageService.getFailureMessageOrEmpty(null)).isEmpty();
        assertThat(messageService.getFailureMessageOrEmpty("  ")).isEmpty();
    }

    @Test
    @DisplayName("메시지 키가 없는 코드는 예외 대신 코드 그대로 — 한 행이 목록 전체를 500 으로 만들지 않는다")
    void 미등록_코드() {
        given(messageSource.getMessage(eq("NOT_PALM_IMAGE"), isNull(), any(Locale.class)))
                .willThrow(new NoSuchMessageException("NOT_PALM_IMAGE"));
        assertThat(messageService.getFailureMessageOrEmpty("not_palm_image")).isEqualTo("not_palm_image");
    }
}
