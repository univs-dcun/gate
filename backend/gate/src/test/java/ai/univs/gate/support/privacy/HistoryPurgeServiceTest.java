package ai.univs.gate.support.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ai.univs.gate.modules.feature.domain.enums.MatchType;
import ai.univs.gate.support.file.FileService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 이력 한 배치를 <b>무엇까지</b> 지우는가 (UG-282).
 *
 * <p>가장 위험한 실수는 지울 파일을 잘못 고르는 것이다. {@code feature_image_path} 는 살아
 * 있는 {@code biometric_feature} 와 같은 파일을 가리키므로, 이력을 지우면서 그것까지 지우면
 * <b>등록된 사용자의 사진이 사라진다.</b>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UG-282: 이력 배치 정리")
class HistoryPurgeServiceTest {

    @Mock
    private HistoryPurgeRepository repository;

    @Mock
    private FileService fileService;

    @InjectMocks
    private HistoryPurgeService service;

    private static MatchHistoryPurgeTarget target(
            Long id, MatchType type, String probe, String feature) {
        return new MatchHistoryPurgeTarget(id, type, probe, feature);
    }

    private static final LocalDateTime CUTOFF = LocalDateTime.now(ZoneOffset.UTC).minusDays(30);

    /**
     * 대상이 없으면 삭제 쿼리도 파일 삭제도 없다.
     *
     * <p>빈 목록으로 {@code DELETE ... WHERE id IN ()} 를 날리면 DB 에 따라 문법 오류다.
     */
    @Test
    @DisplayName("대상이 없으면 아무것도 지우지 않는다")
    void 대상이_없으면_지우지_않는다() {
        given(repository.findMatchHistoryToPurge(any(), anyInt())).willReturn(List.of());

        int deleted = service.purgeMatchHistoryBatch(CUTOFF, 500);

        assertThat(deleted).isZero();
        verify(repository, never()).deleteMatchHistory(anyList());
        verifyNoInteractions(fileService);
    }

    /**
     * <b>이 테스트가 이 클래스의 핵심이다.</b>
     *
     * <p>지우는 파일은 그 시도에서 올린 프로브 이미지
     * ({@code matched_feature_image_path})뿐이다. 리포지토리가 {@code feature_image_path} 를
     * 아예 가져오지 않는 것이 1차 방어이고, 이 테스트가 그 계약을 고정한다 — 조회에 컬럼을
     * 하나 더 얹는 순간 깨진다.
     */
    @Test
    @DisplayName("지우는 이미지는 그 시도의 프로브 이미지뿐이다")
    void 프로브_이미지만_지운다() {
        given(repository.findMatchHistoryToPurge(any(), anyInt())).willReturn(List.of(
                target(1L, MatchType.IDENTIFY, "probe/a.jpg", "feat/shared.jpg"),
                target(2L, MatchType.IDENTIFY, "probe/b.jpg", "feat/shared.jpg")));
        given(repository.deleteMatchHistory(anyList())).willReturn(2);

        int deleted = service.purgeMatchHistoryBatch(CUTOFF, 500);

        assertThat(deleted).isEqualTo(2);
        verify(fileService).delete("probe/a.jpg");
        verify(fileService).delete("probe/b.jpg");

        ArgumentCaptor<List<Long>> ids = ArgumentCaptor.captor();
        verify(repository).deleteMatchHistory(ids.capture());
        assertThat(ids.getValue()).containsExactly(1L, 2L);
    }

    /**
     * 동의를 받지 않은 프로젝트는 이미지를 올리지 않으므로 경로가 비어 있다
     * ({@code FileService.uploadIfConsent}). 그것을 그대로 저장소에 넘기면 의미 없는 예외가
     * 배치마다 난다.
     */
    @Test
    @DisplayName("이미지 경로가 비어 있으면 저장소를 부르지 않는다")
    void 빈_경로는_건너뛴다() {
        given(repository.findMatchHistoryToPurge(any(), anyInt())).willReturn(List.of(
                target(1L, MatchType.IDENTIFY, "", ""),
                target(2L, MatchType.IDENTIFY, null, null)));
        given(repository.deleteMatchHistory(anyList())).willReturn(2);

        service.purgeMatchHistoryBatch(CUTOFF, 500);

        verifyNoInteractions(fileService);
    }

    /**
     * 저장소 장애가 정리를 막지 않는다.
     *
     * <p>여기서 예외를 올리면 저장소 한 번의 장애로 이력 정리가 영구히 멈추고, 그러면 프로브
     * 이미지가 계속 쌓인다 — 막으려던 것이 더 나빠진다.
     */
    @Test
    @DisplayName("이미지 삭제가 실패해도 행은 지운다")
    void 이미지_실패는_행_삭제를_막지_않는다() {
        given(repository.findMatchHistoryToPurge(any(), anyInt())).willReturn(List.of(
                target(1L, MatchType.IDENTIFY, "probe/a.jpg", "feat/shared.jpg"),
                target(2L, MatchType.IDENTIFY, "probe/b.jpg", "feat/shared.jpg")));
        willThrow(new IllegalStateException("MinIO 장애")).given(fileService).delete("probe/a.jpg");
        given(repository.deleteMatchHistory(anyList())).willReturn(2);

        int deleted = service.purgeMatchHistoryBatch(CUTOFF, 500);

        assertThat(deleted).isEqualTo(2);
        // 첫 건이 터져도 나머지는 계속 지운다.
        verify(fileService).delete("probe/b.jpg");
        verify(repository).deleteMatchHistory(List.of(1L, 2L));
    }

    /**
     * 행보다 파일을 먼저 지운다.
     *
     * <p>순서가 뒤집히면 커밋 직후 죽었을 때 아무도 가리키지 않는 파일이 남고, 그 파일은 어느
     * 경로로도 다시 찾을 수 없다.
     */
    @Test
    @DisplayName("파일을 지운 뒤에 행을 지운다")
    void 파일이_먼저다() {
        given(repository.findMatchHistoryToPurge(any(), anyInt()))
                .willReturn(List.of(target(1L, MatchType.IDENTIFY, "probe/a.jpg", "feat/shared.jpg")));
        given(repository.deleteMatchHistory(anyList())).willReturn(1);

        service.purgeMatchHistoryBatch(CUTOFF, 500);

        var 순서 = org.mockito.Mockito.inOrder(fileService, repository);
        순서.verify(fileService).delete("probe/a.jpg");
        순서.verify(repository).deleteMatchHistory(anyList());
    }

    /** 특징점 이력은 행만 지운다 — 가진 경로가 공유 경로라 건드리면 안 된다. */
    @Test
    @DisplayName("특징점 이력은 파일을 건드리지 않는다")
    void 특징점_이력은_행만_지운다() {
        given(repository.findFeatureHistoryToPurge(any(), anyInt())).willReturn(List.of(7L, 8L));
        given(repository.deleteFeatureHistory(anyList())).willReturn(2);

        int deleted = service.purgeFeatureHistoryBatch(CUTOFF, 500);

        assertThat(deleted).isEqualTo(2);
        verifyNoInteractions(fileService);
        verify(repository).deleteFeatureHistory(List.of(7L, 8L));
    }

    @Test
    @DisplayName("특징점 이력도 대상이 없으면 삭제 쿼리를 날리지 않는다")
    void 특징점_이력_대상이_없으면() {
        given(repository.findFeatureHistoryToPurge(any(), anyInt())).willReturn(List.of());

        assertThat(service.purgeFeatureHistoryBatch(CUTOFF, 500)).isZero();
        verify(repository, never()).deleteFeatureHistory(anyList());
    }

    /**
     * <b>등록 사진은 절대 지우지 않는다</b> — 1:N·1:1(id) 계열.
     *
     * <p>{@code MatchHistory.updateBiometricFeature} 가 {@code feature_image_path} 에 살아 있는
     * {@code biometric_feature} 의 경로를 <b>복사해</b> 넣는다. 같은 파일을 그 특징점과 다른
     * 이력 행들이 함께 가리킨다. 여기서 지우면 등록된 사용자의 사진이 사라진다.
     */
    @Test
    @DisplayName("IDENTIFY 행은 등록 사진 경로를 건드리지 않는다")
    void 등록_사진은_남긴다() {
        given(repository.findMatchHistoryToPurge(any(), anyInt())).willReturn(
                List.of(target(1L, MatchType.IDENTIFY, "probe/p.jpg", "feat/registered.jpg")));
        given(repository.deleteMatchHistory(anyList())).willReturn(1);

        service.purgeMatchHistoryBatch(CUTOFF, 500);

        verify(fileService).delete("probe/p.jpg");
        verify(fileService, never()).delete("feat/registered.jpg");
    }

    /**
     * <b>VERIFY_IMAGE 의 신분증 사진은 지운다</b> (반박 리뷰가 잡은 결함).
     *
     * <p>사진 두 장을 맞춰 보는 1:1 은 등록된 특징점이 개입하지 않는다.
     * {@code FaceVerifyByFeatureImageUseCase} 가 {@code feature_image_path} 에 <b>그 요청에서
     * 올린 신분증 이미지</b>를 넣고, 성공 전이도 1:1 변형이라 덮어써지지 않는다.
     *
     * <p>이 행에서 그 경로를 남기면, 행이 지워진 뒤 <b>아무도 가리키지 않는 신분증 파일이
     * 영구히 남는다.</b> 이 기능이 막으려던 바로 그 상태를 정상 동작 시 매 행마다 만든다.
     */
    @Test
    @DisplayName("VERIFY_IMAGE 행은 신분증 사진도 함께 지운다")
    void 신분증_사진도_지운다() {
        given(repository.findMatchHistoryToPurge(any(), anyInt())).willReturn(
                List.of(target(1L, MatchType.VERIFY_IMAGE, "probe/p.jpg", "doc/id-card.jpg")));
        given(repository.deleteMatchHistory(anyList())).willReturn(1);

        service.purgeMatchHistoryBatch(CUTOFF, 500);

        verify(fileService).delete("probe/p.jpg");
        verify(fileService).delete("doc/id-card.jpg");
    }

    /** 동의가 없어 한쪽만 비어 있어도 나머지는 지운다. */
    @Test
    @DisplayName("VERIFY_IMAGE 에서 한쪽 경로가 비어도 나머지는 지운다")
    void 한쪽만_비어도_지운다() {
        given(repository.findMatchHistoryToPurge(any(), anyInt())).willReturn(
                List.of(target(1L, MatchType.VERIFY_IMAGE, null, "doc/id-card.jpg")));
        given(repository.deleteMatchHistory(anyList())).willReturn(1);

        service.purgeMatchHistoryBatch(CUTOFF, 500);

        verify(fileService).delete("doc/id-card.jpg");
    }
}
