package ai.univs.gate.support.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UG-347: 삭제한 특징점의 이미지 정기 정리")
class DeletedFeatureImagePurgeSchedulerTest {

    private final DeletedFeatureImagePurgeService service = mock(DeletedFeatureImagePurgeService.class);

    private DeletedFeatureImagePurgeScheduler scheduler(boolean enabled) {
        DeletedFeatureImagePurgeScheduler s = new DeletedFeatureImagePurgeScheduler(service);
        s.enabled = enabled;
        return s;
    }

    private static List<Long> ids(long from, long count) {
        return LongStream.range(from, from + count).boxed().toList();
    }

    @Test
    @DisplayName("한 건이 실패해도 나머지를 계속 지운다")
    void 실패_격리() {
        given(service.findTargets(0L, DeletedFeatureImagePurgeScheduler.MAX_PER_RUN)).willReturn(List.of(1L, 2L, 3L));
        willThrow(new IllegalStateException("disk")).given(service).purge(2L);
        given(service.purge(1L)).willReturn(true);
        given(service.purge(3L)).willReturn(true);

        scheduler(true).purgeRemaining();

        verify(service).purge(1L);
        verify(service).purge(3L);
    }

    @Test
    @DisplayName("상한만큼 나오면 다음 실행은 마지막 id 다음부터, 적게 나오면 처음부터 — 계속 실패하는 건이 앞을 막지 않는다")
    void 커서() {
        int max = DeletedFeatureImagePurgeScheduler.MAX_PER_RUN;
        given(service.findTargets(0L, max)).willReturn(ids(1, max));
        given(service.findTargets((long) max, max)).willReturn(List.of(9_000L));
        DeletedFeatureImagePurgeScheduler s = scheduler(true);

        s.purgeRemaining();
        assertThat(s.cursor).isEqualTo(max);
        s.purgeRemaining();
        assertThat(s.cursor).as("끝까지 왔으니 처음부터").isZero();
        verify(service).purge(9_000L);
    }

    @Test
    @DisplayName("꺼져 있으면 조회도 하지 않는다")
    void 꺼짐() {
        scheduler(false).purgeRemaining();

        verify(service, never()).findTargets(anyLong(), anyInt());
    }
}
