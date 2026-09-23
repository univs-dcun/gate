package ai.univs.gate.support.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ai.univs.gate.support.file.FileService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
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
 * <p>규칙은 하나다 — 지우는 행이 들고 있던 이미지는, 살아 있는 {@code biometric_feature} 가
 * 가리키지 않는 한 함께 지운다. 이 클래스는 그 규칙의 양쪽 방향을 고정한다.
 *
 * <ul>
 *   <li>덜 지우면 <b>영구 고아</b>가 된다 — 행이 사라진 뒤 아무도 가리키지 않는 개인정보
 *       파일이 남고, 어느 경로로도 다시 찾을 수 없다.
 *   <li>더 지우면 <b>등록된 사용자의 사진이 사라진다.</b> 되돌릴 수 없다.
 * </ul>
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

    private static final LocalDateTime CUTOFF = LocalDateTime.now(ZoneOffset.UTC).minusDays(30);

    private static MatchHistoryPurgeTarget target(Long id, String probe, String feature) {
        return new MatchHistoryPurgeTarget(id, probe, feature);
    }

    private void 대상(MatchHistoryPurgeTarget... rows) {
        given(repository.findMatchHistoryToPurge(any(), anyInt())).willReturn(List.of(rows));
    }

    private void 살아있는_특징점이_가리키는_경로(String... paths) {
        given(repository.findPathsStillReferencedByFeatures(anyCollection()))
                .willReturn(Set.of(paths));
    }

    private void 지운_행(int count) {
        given(repository.deleteMatchHistory(anyList())).willReturn(count);
    }

    /**
     * 대상이 없으면 참조 조회도 삭제 쿼리도 없다.
     *
     * <p>빈 목록으로 {@code DELETE ... WHERE id IN ()} 를 날리면 DB 에 따라 문법 오류다.
     */
    @Test
    @DisplayName("대상이 없으면 아무것도 하지 않는다")
    void 대상이_없으면_지우지_않는다() {
        given(repository.findMatchHistoryToPurge(any(), anyInt())).willReturn(List.of());

        assertThat(service.purgeMatchHistoryBatch(CUTOFF, 500)).isZero();

        verify(repository, never()).deleteMatchHistory(anyList());
        verify(repository, never()).findPathsStillReferencedByFeatures(anyCollection());
        verifyNoInteractions(fileService);
    }

    /**
     * <b>등록 사진은 지우지 않는다.</b>
     *
     * <p>이력의 {@code feature_image_path} 는 대개 살아 있는 {@code biometric_feature} 의 경로를
     * <b>복사해</b> 넣은 값이다. 같은 파일을 그 특징점과 다른 이력 행들이 함께 가리킨다. 여기서
     * 지우면 등록된 사용자의 사진이 사라진다.
     */
    @Test
    @DisplayName("살아 있는 특징점이 가리키는 파일은 남긴다")
    void 참조되는_파일은_남긴다() {
        대상(target(1L, "probe/p.jpg", "feat/registered.jpg"));
        살아있는_특징점이_가리키는_경로("feat/registered.jpg");
        지운_행(1);

        service.purgeMatchHistoryBatch(CUTOFF, 500);

        verify(fileService).delete("probe/p.jpg");
        verify(fileService, never()).delete("feat/registered.jpg");
    }

    /**
     * <b>신분증 사진은 지운다</b> (반박 리뷰 1차가 잡은 결함).
     *
     * <p>{@code VERIFY_IMAGE} 는 사진 두 장을 맞춰 보는 1:1 이라 등록된 특징점이 개입하지
     * 않는다. {@code FaceVerifyByFeatureImageUseCase} 가 {@code feature_image_path} 에 그
     * 요청에서 올린 신분증 이미지를 넣고, 성공 전이도 1:1 변형이라 덮어써지지 않는다.
     *
     * <p>그 파일은 어떤 특징점도 가리키지 않으므로 이 규칙에서 자동으로 삭제 대상이 된다 —
     * {@code match_type} 을 볼 필요가 없다.
     */
    @Test
    @DisplayName("어떤 특징점도 가리키지 않는 파일은 지운다 — 신분증 이미지가 그렇다")
    void 참조되지_않는_파일은_지운다() {
        대상(target(1L, "probe/p.jpg", "doc/id-card.jpg"));
        살아있는_특징점이_가리키는_경로();
        지운_행(1);

        service.purgeMatchHistoryBatch(CUTOFF, 500);

        verify(fileService).delete("probe/p.jpg");
        verify(fileService).delete("doc/id-card.jpg");
    }

    /**
     * <b>같은 컬럼이라도 행마다 답이 다르다</b> (반박 리뷰 2차가 잡은 결함).
     *
     * <p>레거시 {@code VERIFY} 행은 by-id(공유 경로)와 by-image(전용 경로)가 <b>같은
     * {@code match_type} 값</b>을 쓴다 — 2026-05 ec9e6d4 이전에는 두 API 가 갈려 있지 않았다.
     * 타입 화이트리스트로는 이 두 행을 구분할 수 없다.
     *
     * <p>참조로 정하면 구분이 자동으로 된다. 이 테스트는 <b>한 배치 안에 두 모양이 섞여</b>
     * 있어도 각각 맞게 처리되는지를 본다.
     */
    @Test
    @DisplayName("한 배치에 공유 경로와 전용 경로가 섞여도 각각 맞게 처리한다")
    void 같은_배치에_두_모양이_섞여도() {
        대상(
                target(1L, "probe/a.jpg", "feat/registered.jpg"),  // 레거시 VERIFY (by-id)
                target(2L, "probe/b.jpg", "doc/id-card.jpg"));     // 레거시 VERIFY (by-image)
        살아있는_특징점이_가리키는_경로("feat/registered.jpg");
        지운_행(2);

        service.purgeMatchHistoryBatch(CUTOFF, 500);

        verify(fileService).delete("probe/a.jpg");
        verify(fileService).delete("probe/b.jpg");
        verify(fileService).delete("doc/id-card.jpg");
        verify(fileService, never()).delete("feat/registered.jpg");
    }

    /**
     * <b>두 컬럼이 모두 참조 검사에 올라간다.</b>
     *
     * <p>규칙은 "행이 들고 있던 <b>모든</b> 이미지가 검사를 거친다" 인데, 나머지 테스트는 전부
     * 참조 조회를 인자 무시로 스텁하므로 그것을 확인하지 못한다. 한쪽 컬럼만 넣도록 바뀌어도
     * 스위트가 초록이었다 — 3차 반박 리뷰가 변이 둘(M14·M17)로 증명했다.
     *
     * <p>한쪽만 넣으면 <b>등록된 사용자의 사진이 삭제된다.</b> 되돌릴 수 없다.
     *
     * <p>초판은 두 컬럼에 <b>같은 값</b>을 넣어 이 자리를 막으려 했는데, 그러면 어느 쪽이
     * 올라갔는지 구분되지 않아 아무것도 증명하지 못한다. 슬라이스 테스트 javadoc 이 스스로
     * 금지한 방식이었다.
     *
     * <p>덤으로, 등록 이미지가 {@code matched_feature_image_path} 쪽에 들어 있는 행
     * ({@code match_history} 의 REGISTER 잔존 행, V26)도 이 규칙으로 지켜진다는 것을 함께
     * 고정한다 — 참조 검사는 컬럼을 가리지 않는다.
     */
    @Test
    @DisplayName("두 컬럼의 경로가 모두 참조 검사에 올라간다")
    void 두_컬럼이_모두_검사된다() {
        대상(target(1L, "feat/registered.jpg", "probe/p.jpg"));
        살아있는_특징점이_가리키는_경로("feat/registered.jpg");
        지운_행(1);

        service.purgeMatchHistoryBatch(CUTOFF, 500);

        ArgumentCaptor<java.util.Collection<String>> 검사대상 = ArgumentCaptor.captor();
        verify(repository).findPathsStillReferencedByFeatures(검사대상.capture());
        assertThat(검사대상.getValue())
                .as("한쪽만 올리면 나머지 한쪽은 검사 없이 지워진다")
                .containsExactlyInAnyOrder("feat/registered.jpg", "probe/p.jpg");

        verify(fileService).delete("probe/p.jpg");
        verify(fileService, never()).delete("feat/registered.jpg");
        verify(repository).deleteMatchHistory(List.of(1L));
    }

    /**
     * 동의를 받지 않은 프로젝트는 이미지를 올리지 않으므로 경로가 비어 있다 —
     * {@code uploadIfConsent} 는 {@code null}, 업로드가 꺼진 환경은 빈 문자열이다. 그것을 그대로
     * 참조 조회와 저장소에 넘기면 의미 없는 예외가 배치마다 난다.
     */
    @Test
    @DisplayName("빈 경로는 참조 조회에도 저장소에도 넘기지 않는다")
    void 빈_경로는_건너뛴다() {
        대상(target(1L, "", ""), target(2L, null, null));
        살아있는_특징점이_가리키는_경로();
        지운_행(2);

        service.purgeMatchHistoryBatch(CUTOFF, 500);

        verifyNoInteractions(fileService);

        ArgumentCaptor<java.util.Collection<String>> paths = ArgumentCaptor.captor();
        verify(repository).findPathsStillReferencedByFeatures(paths.capture());
        assertThat(paths.getValue()).isEmpty();
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
        대상(target(1L, "probe/a.jpg", null), target(2L, "probe/b.jpg", null));
        살아있는_특징점이_가리키는_경로();
        willThrow(new IllegalStateException("MinIO 장애")).given(fileService).delete("probe/a.jpg");
        지운_행(2);

        assertThat(service.purgeMatchHistoryBatch(CUTOFF, 500)).isEqualTo(2);

        verify(fileService).delete("probe/b.jpg");
        verify(repository).deleteMatchHistory(List.of(1L, 2L));
    }

    /**
     * 파일보다 행을 먼저 지우면, 커밋 직후 죽었을 때 아무도 가리키지 않는 파일이 남고 그
     * 파일은 어느 경로로도 다시 찾을 수 없다.
     */
    @Test
    @DisplayName("파일을 지운 뒤에 행을 지운다")
    void 파일이_먼저다() {
        대상(target(1L, "probe/a.jpg", null));
        살아있는_특징점이_가리키는_경로();
        지운_행(1);

        service.purgeMatchHistoryBatch(CUTOFF, 500);

        var 순서 = org.mockito.Mockito.inOrder(fileService, repository);
        순서.verify(fileService).delete("probe/a.jpg");
        순서.verify(repository).deleteMatchHistory(anyList());
    }

    /**
     * <b>특징점 이력도 같은 규칙을 거친다.</b>
     *
     * <p>지금 그 테이블의 경로는 전부 등록된 특징점에서 복사된 값이라 결과적으로 지울 것이
     * 없다. 그래도 같은 코드를 지나게 한 이유는, 자기 이미지를 올리는 사건 종류가 추가되는
     * 순간 여기만 조용히 영구 고아를 만들기 때문이다 — 이 티켓에서 두 번 겪은 실패 모드다.
     */
    @Test
    @DisplayName("특징점 이력도 참조 검사를 거친다")
    void 특징점_이력도_같은_규칙() {
        given(repository.findFeatureHistoryToPurge(any(), anyInt()))
                .willReturn(List.of(target(7L, null, "feat/registered.jpg")));
        살아있는_특징점이_가리키는_경로("feat/registered.jpg");
        given(repository.deleteFeatureHistory(anyList())).willReturn(1);

        assertThat(service.purgeFeatureHistoryBatch(CUTOFF, 500)).isEqualTo(1);

        verify(repository).findPathsStillReferencedByFeatures(anyCollection());
        verify(fileService, never()).delete("feat/registered.jpg");
        verify(repository).deleteFeatureHistory(List.of(7L));
    }

    @Test
    @DisplayName("특징점 이력도 대상이 없으면 삭제 쿼리를 날리지 않는다")
    void 특징점_이력_대상이_없으면() {
        given(repository.findFeatureHistoryToPurge(any(), anyInt())).willReturn(List.of());

        assertThat(service.purgeFeatureHistoryBatch(CUTOFF, 500)).isZero();
        verify(repository, never()).deleteFeatureHistory(anyList());
    }
}
