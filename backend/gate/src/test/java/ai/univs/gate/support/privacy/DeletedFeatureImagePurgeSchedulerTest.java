package ai.univs.gate.support.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ai.univs.gate.support.privacy.DeletedFeatureImagePurgeService.Outcome;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UG-347: 삭제한 특징점의 이미지 정기 정리")
class DeletedFeatureImagePurgeSchedulerTest {

    private final DeletedFeatureImagePurgeService service = mock(DeletedFeatureImagePurgeService.class);

    private DeletedFeatureImagePurgeScheduler scheduler(boolean enabled) {
        given(service.isEnabled()).willReturn(enabled);
        return new DeletedFeatureImagePurgeScheduler(service);
    }

    private static List<Long> ids(long from, long count) {
        return LongStream.range(from, from + count).boxed().toList();
    }

    @Test
    @DisplayName("한 건이 실패해도 나머지를 계속 지운다")
    void 실패_격리() {
        given(service.findTargets(0L, DeletedFeatureImagePurgeScheduler.MAX_PER_RUN)).willReturn(List.of(1L, 2L, 3L));
        willThrow(new IllegalStateException("disk")).given(service).purge(2L);
        given(service.purge(1L)).willReturn(Outcome.PURGED);
        given(service.purge(3L)).willReturn(Outcome.ALREADY_GONE);

        var summary = scheduler(true).runOnce();

        verify(service).purge(1L);
        verify(service).purge(3L);
        assertThat(summary.ran()).isTrue();
        assertThat(summary.targets()).isEqualTo(3);
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.outcomes()).containsEntry(Outcome.PURGED, 1).containsEntry(Outcome.ALREADY_GONE, 1);
        assertThat(summary.firstFailure()).contains("featureSeq=2").contains("IllegalStateException").contains("disk");
        assertThat(summary.allFailed()).isFalse();
    }

    @Test
    @DisplayName("대상이 모두 실패하면 allFailed — 저장소 문제 경보 조건. 첫 실패는 처음 것만 남긴다")
    void 모두_실패() {
        given(service.findTargets(0L, DeletedFeatureImagePurgeScheduler.MAX_PER_RUN)).willReturn(List.of(1L, 2L));
        willThrow(new IllegalStateException("first")).given(service).purge(1L);
        willThrow(new IllegalStateException("second")).given(service).purge(2L);

        var summary = scheduler(true).runOnce();

        assertThat(summary.allFailed()).isTrue();
        assertThat(summary.firstFailure()).contains("featureSeq=1").contains("first").doesNotContain("second");
    }

    @Test
    @DisplayName("대상이 없으면 아무것도 지우지 않고, 실패도 없다")
    void 대상_없음() {
        given(service.findTargets(0L, DeletedFeatureImagePurgeScheduler.MAX_PER_RUN)).willReturn(List.of());

        var summary = scheduler(true).runOnce();

        assertThat(summary.ran()).isTrue();
        assertThat(summary.targets()).isZero();
        assertThat(summary.allFailed()).isFalse();
        verify(service, never()).purge(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("상한만큼 나오면 다음 실행은 마지막 id 다음부터, 적게 나오면 처음부터 — 계속 실패하는 건이 앞을 막지 않는다")
    void 커서() {
        int max = DeletedFeatureImagePurgeScheduler.MAX_PER_RUN;
        given(service.findTargets(0L, max)).willReturn(ids(1, max));
        given(service.findTargets((long) max, max)).willReturn(List.of(9_000L));
        DeletedFeatureImagePurgeScheduler s = scheduler(true);

        s.runOnce();
        assertThat(s.cursor).isEqualTo(max);
        s.runOnce();
        assertThat(s.cursor).as("끝까지 왔으니 처음부터").isZero();
        verify(service).purge(9_000L);
    }

    @Test
    @DisplayName("꺼져 있으면 조회도 하지 않는다")
    void 꺼짐() {
        var s = scheduler(false);

        assertThat(s.runOnce()).isEqualTo(DeletedFeatureImagePurgeScheduler.RunSummary.SKIPPED);
        assertThat(s.disabledLogged).as("꺼짐 안내는 한 번 남기고 표시한다").isTrue();
        s.purgeRemaining();

        verify(service, never()).findTargets(anyLong(), anyInt());
    }
}
