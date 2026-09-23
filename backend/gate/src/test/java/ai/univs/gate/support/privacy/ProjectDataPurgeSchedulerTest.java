package ai.univs.gate.support.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 정리 잡이 <b>언제 도는가</b> (UG-303).
 *
 * <p>이 잡은 되돌릴 수 없는 개인정보 삭제를 한다. 그래서 가장 중요한 성질이 "켜지 않으면
 * 아무것도 하지 않는다" 이고, 그것을 첫 테스트로 둔다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UG-303: 삭제된 프로젝트 데이터 정리 잡")
class ProjectDataPurgeSchedulerTest {

    @Mock
    private ProjectDataPurgeService purgeService;

    @InjectMocks
    private ProjectDataPurgeScheduler scheduler;

    private void retentionDays(int days) {
        ReflectionTestUtils.setField(scheduler, "retentionDays", days);
    }

    /**
     * 기본값에서 아무것도 하지 않는다.
     *
     * <p><b>이 테스트가 이 클래스의 존재 이유다.</b> 보존 기간은 제품·법무가 정할 값이고, 그
     * 답이 나오기 전에 코드가 기본값으로 지우기 시작하면 되돌릴 방법이 없다. 대상 조회조차
     * 하지 않는지를 본다 — 조회만 해도 로그가 남아 "돌고 있다" 는 오해를 만든다.
     */
    @Test
    @DisplayName("보존 기간을 주지 않으면 대상 조회조차 하지 않는다")
    void 설정_없으면_아무것도_안한다() {
        retentionDays(0);

        scheduler.purgeDeletedProjectData();

        verifyNoInteractions(purgeService);
    }

    @Test
    @DisplayName("음수도 꺼진 것으로 본다 — 오타가 전량 삭제가 되지 않게")
    void 음수도_꺼진다() {
        retentionDays(-1);

        scheduler.purgeDeletedProjectData();

        verifyNoInteractions(purgeService);
    }

    @Test
    @DisplayName("보존 기간을 주면 그만큼 지난 프로젝트를 찾는다")
    void 기준시각은_보존기간만큼_과거다() {
        retentionDays(30);
        given(purgeService.findPurgeTargets(any(), anyInt())).willReturn(List.of());

        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);
        scheduler.purgeDeletedProjectData();
        LocalDateTime after = LocalDateTime.now(ZoneOffset.UTC);

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(purgeService).findPurgeTargets(cutoff.capture(), anyInt());

        assertThat(cutoff.getValue())
                .as("기준이 현재에 가까워지면 유예가 사라져 방금 삭제한 프로젝트까지 지운다")
                .isBetween(before.minusDays(30).minusSeconds(5), after.minusDays(30));
    }

    @Test
    @DisplayName("찾은 프로젝트를 하나씩 정리한다")
    void 대상을_정리한다() {
        retentionDays(30);
        given(purgeService.findPurgeTargets(any(), anyInt())).willReturn(List.of(1L, 2L, 3L));
        given(purgeService.purgeProject(any())).willReturn(2);

        scheduler.purgeDeletedProjectData();

        verify(purgeService).purgeProject(1L);
        verify(purgeService).purgeProject(2L);
        verify(purgeService).purgeProject(3L);
    }

    /**
     * 한 프로젝트의 실패가 나머지를 막지 않는다.
     *
     * <p>막으면 하위 서비스 한 대의 장애가 정리 전체를 영구히 세운다 — 그 사이 지워졌어야 할
     * 개인정보가 계속 남는다.
     */
    @Test
    @DisplayName("한 프로젝트가 실패해도 나머지를 계속 정리한다")
    void 실패해도_나머지를_계속한다() {
        retentionDays(30);
        given(purgeService.findPurgeTargets(any(), anyInt())).willReturn(List.of(1L, 2L, 3L));
        given(purgeService.purgeProject(1L)).willThrow(new IllegalStateException("하위 서비스 장애"));
        given(purgeService.purgeProject(2L)).willReturn(1);
        given(purgeService.purgeProject(3L)).willReturn(1);

        scheduler.purgeDeletedProjectData();

        verify(purgeService).purgeProject(2L);
        verify(purgeService).purgeProject(3L);
    }

    /**
     * 실행당 상한.
     *
     * <p>누적분이 많은 첫 실행에서 수천 개 프로젝트의 하위 서비스 삭제를 한 번에 쏘면 그쪽이
     * 먼저 넘어간다. 나눠서 여러 밤에 걸쳐 끝낸다.
     */
    @Test
    @DisplayName("실행당 상한을 넘겨 조회하지 않는다")
    void 실행당_상한이_있다() {
        retentionDays(30);
        given(purgeService.findPurgeTargets(any(), anyInt())).willReturn(List.of());

        scheduler.purgeDeletedProjectData();

        ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
        verify(purgeService).findPurgeTargets(any(), limit.capture());

        assertThat(limit.getValue())
                .as("상한이 사라지면 첫 실행이 하위 서비스를 밀어 버린다")
                .isPositive()
                .isLessThanOrEqualTo(500);
    }

    @Test
    @DisplayName("대상이 없으면 정리를 호출하지 않는다")
    void 대상이_없으면_조용하다() {
        retentionDays(30);
        given(purgeService.findPurgeTargets(any(), anyInt())).willReturn(List.of());

        scheduler.purgeDeletedProjectData();

        verify(purgeService, never()).purgeProject(any());
    }

    @Test
    @DisplayName("상한만큼 찾으면 전부 정리한다 — 조용히 일부만 처리하지 않는다")
    void 상한까지는_전부_처리한다() {
        retentionDays(30);
        List<Long> full = IntStream.rangeClosed(1, 50).mapToObj(Long::valueOf).toList();
        given(purgeService.findPurgeTargets(any(), anyInt())).willReturn(full);
        given(purgeService.purgeProject(any())).willReturn(0);

        scheduler.purgeDeletedProjectData();

        full.forEach(id -> verify(purgeService).purgeProject(id));
    }
}
