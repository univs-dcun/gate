package ai.univs.gate.support.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 이력 정리 잡이 <b>언제 도는가</b> (UG-282).
 *
 * <p>{@link ProjectDataPurgeSchedulerTest} 와 같은 이유로 "켜지 않으면 아무것도 하지 않는다"
 * 를 첫 테스트로 둔다. 다만 이쪽은 위험이 한 단계 더 크다 — 이 이력은 <b>지우면 안 될 수도
 * 있는</b> 데이터다(위탁자 쪽 법정 보존 의무).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UG-282: 이력 보존 정리 잡")
class HistoryPurgeSchedulerTest {

    @Mock
    private HistoryPurgeService purgeService;

    @InjectMocks
    private HistoryPurgeScheduler scheduler;

    private void retentionDays(int days) {
        ReflectionTestUtils.setField(scheduler, "retentionDays", days);
    }

    /**
     * 기본값에서 아무것도 하지 않는다.
     *
     * <p><b>이 테스트가 이 클래스의 존재 이유다.</b> 코드가 보존 기간의 기본값을 정하면 금융
     * 고객사의 법정 보존 의무(특정금융정보법 제5조의4 등)를 우리가 깨는 형태가 된다. 대상
     * 조회조차 하지 않는지를 본다.
     */
    @Test
    @DisplayName("보존 기간을 주지 않으면 대상 조회조차 하지 않는다")
    void 설정_없으면_아무것도_안한다() {
        retentionDays(0);

        scheduler.purgeExpiredHistory();

        verifyNoInteractions(purgeService);
    }

    @Test
    @DisplayName("음수도 꺼진 것으로 본다")
    void 음수도_꺼진다() {
        retentionDays(-1);

        scheduler.purgeExpiredHistory();

        verifyNoInteractions(purgeService);
    }

    /**
     * 기준 시각이 "지금 - 보존일" 인지.
     *
     * <p>부호가 뒤집히면 <b>최근 이력부터</b> 지운다. 단위 테스트로 잡지 못하면 운영에서
     * 알아차릴 방법이 없다.
     */
    @Test
    @DisplayName("기준 시각은 지금에서 보존일을 뺀 값이다")
    void 기준_시각() {
        retentionDays(1825);
        LocalDateTime 호출_전 = LocalDateTime.now(ZoneOffset.UTC);

        scheduler.purgeExpiredHistory();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(purgeService).purgeMatchHistoryBatch(cutoff.capture(), anyInt());

        assertThat(cutoff.getValue())
                .as("보존 1825일이면 기준은 대략 5년 전이어야 한다")
                .isBefore(호출_전.minusDays(1824))
                .isAfter(호출_전.minusDays(1826));
    }

    /** 두 종류를 같은 기준으로 지워야 한다 — 한쪽만 지우면 통합 목록(ActivityLog)이 어긋난다. */
    @Test
    @DisplayName("인증 이력과 특징점 이력을 같은 기준으로 지운다")
    void 두_종류를_모두_지운다() {
        retentionDays(30);

        scheduler.purgeExpiredHistory();

        ArgumentCaptor<LocalDateTime> m = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> f = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(purgeService).purgeMatchHistoryBatch(m.capture(), anyInt());
        verify(purgeService).purgeFeatureHistoryBatch(f.capture(), anyInt());

        assertThat(m.getValue()).isEqualTo(f.getValue());
    }

    /**
     * 0 이 나오면 멈춘다.
     *
     * <p>멈추지 않으면 지울 것이 없는 날에도 상한(20배치)만큼 헛돈다. 그 쿼리 하나하나가
     * 최대 테이블 조회다.
     */
    @Test
    @DisplayName("더 지울 것이 없으면 그 자리에서 멈춘다")
    void 빈_배치에서_멈춘다() {
        retentionDays(30);
        given(purgeService.purgeMatchHistoryBatch(any(), anyInt())).willReturn(0);
        given(purgeService.purgeFeatureHistoryBatch(any(), anyInt())).willReturn(0);

        scheduler.purgeExpiredHistory();

        verify(purgeService, times(1)).purgeMatchHistoryBatch(any(), anyInt());
        verify(purgeService, times(1)).purgeFeatureHistoryBatch(any(), anyInt());
    }

    /**
     * 남아 있으면 이어서 지운다.
     *
     * <p>배치가 가득 차 돌아오면 아직 남았다는 뜻이다. 한 배치만 지우고 끝내면 밀린 분량이
     * 하루 500행씩만 줄어 영원히 따라잡지 못한다.
     */
    @Test
    @DisplayName("배치가 가득 차면 상한까지 이어서 지운다")
    void 상한까지_이어_지운다() {
        retentionDays(30);
        given(purgeService.purgeMatchHistoryBatch(any(), anyInt())).willReturn(500);
        given(purgeService.purgeFeatureHistoryBatch(any(), anyInt())).willReturn(0);

        scheduler.purgeExpiredHistory();

        verify(purgeService, times(20)).purgeMatchHistoryBatch(any(), anyInt());
    }

    /**
     * 한 종류가 터져도 다른 종류는 돈다.
     *
     * <p>{@code feature_history} 쪽 장애가 {@code match_history} 정리를 영구히 막으면, 정작
     * 프로브 이미지(개인정보의 본체)가 계속 쌓인다.
     */
    @Test
    @DisplayName("인증 이력이 터져도 특징점 이력은 계속 지운다")
    void 한쪽_실패가_다른쪽을_막지_않는다() {
        retentionDays(30);
        given(purgeService.purgeMatchHistoryBatch(any(), anyInt()))
                .willThrow(new IllegalStateException("DB 장애"));
        given(purgeService.purgeFeatureHistoryBatch(any(), anyInt())).willReturn(0);

        scheduler.purgeExpiredHistory();

        verify(purgeService).purgeFeatureHistoryBatch(any(), anyInt());
    }
}
