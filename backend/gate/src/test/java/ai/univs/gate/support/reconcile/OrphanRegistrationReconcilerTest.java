package ai.univs.gate.support.reconcile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ai.univs.gate.modules.feature.domain.entity.FeatureHistory;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.DeleteFaceFeignRequestDTO;
import ai.univs.gate.modules.feature.infrastructure.client.palm.dto.DeletePalmFeignRequestDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.support.feature.face.FaceService;
import ai.univs.gate.support.feature.palm.PalmService;
import ai.univs.gate.support.history.HistoryRecorder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.stream.IntStream;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 결과를 모르는 등록 행 하나를 어떻게 처리하는가 (UG-338).
 *
 * <p>되돌릴 수 없는 삭제를 한다. 가장 중요한 성질은 두 가지다 — <b>gate 에 있는 특징점은 절대 지우지
 * 않는다</b>, 그리고 <b>하위 삭제를 확인하지 못하면 닫지 않는다</b>(닫으면 id 가 지워져 고아를 영영 못
 * 찾는다).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UG-338: 결과를 모르는 등록 정리")
class OrphanRegistrationReconcilerTest {

    private static final String 발급 = "0f8fad5b-d9cb-469f-a165-70867728950e";

    @Mock private OrphanRegistrationRepository repository;
    @Mock private HistoryRecorder historyRecorder;
    @Mock private FaceService faceService;
    @Mock private PalmService palmService;

    @InjectMocks private OrphanRegistrationReconciler reconciler;

    private static final Project 프로젝트 =
            Project.builder().id(1L).accountId(9L).projectName("p").branchName("branch-A").build();

    private static FeatureHistory 행(FeatureType type) {
        return FeatureHistory.register(프로젝트, type, false, null, "tx", true, 발급);
    }

    private static FeatureHistory 행(long id) {
        FeatureHistory row = 행(FeatureType.FACE);
        ReflectionTestUtils.setField(row, "id", id);
        return row;
    }

    private static List<FeatureHistory> 행들(long from, int count) {
        return IntStream.range(0, count).mapToObj(i -> 행(from + i)).toList();
    }

    /** instant() 를 부를 때마다 step 만큼 흐르는 시계. */
    private static Clock 흐르는_시계(Duration step) {
        return new Clock() {
            private Instant now = Instant.parse("2026-09-28T00:00:00Z");

            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() {
                Instant current = now;
                now = now.plus(step);
                return current;
            }
        };
    }

    private void 켠다() {
        ReflectionTestUtils.setField(reconciler, "enabled", true);
    }

    @Test
    @DisplayName("꺼져 있으면 조회조차 하지 않는다")
    void 꺼지면_아무것도_안한다() {
        ReflectionTestUtils.setField(reconciler, "enabled", false);

        reconciler.reconcile();

        verifyNoInteractions(repository, faceService, palmService, historyRecorder);
    }

    /**
     * 조회 창 — 10분보다 오래되고 24시간보다 새로운 것만.
     *
     * <p>10분보다 짧으면 아직 처리 중인 요청을 되돌린다. 부호가 뒤집히면 방금 들어온 등록을 지운다.
     */
    @Test
    @DisplayName("10분보다 오래되고 24시간보다 새로운 행만 조회한다")
    void 조회_창() {
        켠다();
        given(repository.findStaleRegistrations(any(), any(), anyLong(), anyInt())).willReturn(List.of());
        LocalDateTime 전 = LocalDateTime.now(ZoneOffset.UTC);

        reconciler.reconcile();

        ArgumentCaptor<LocalDateTime> stale = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> oldest = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).findStaleRegistrations(stale.capture(), oldest.capture(), anyLong(), anyInt());
        assertThat(stale.getValue()).isBefore(전.minusMinutes(9)).isAfter(전.minusMinutes(11));
        assertThat(oldest.getValue()).isBefore(전.minusHours(23)).isAfter(전.minusHours(25));
    }

    /** <b>안전장치.</b> 있다면 gate 쓰기가 성공했다는 뜻이다 — 하위를 지우면 살아 있는 템플릿을 지운다. */
    @Test
    @DisplayName("그 id 의 특징점이 gate 에 있으면 하위를 지우지 않고 닫지도 않는다")
    void gate_에_있으면_건드리지_않는다() {
        FeatureHistory row = 행(FeatureType.FACE);
        given(repository.featureExists(발급)).willReturn(true);

        assertThat(reconciler.reconcileOne(row)).isFalse();

        verifyNoInteractions(faceService, palmService, historyRecorder);
        assertThat(row.getFeatureId()).isEqualTo(발급);
    }

    @Test
    @DisplayName("face 에서 지우면 행을 INTERNAL_SERVER_ERROR 로 닫고 id 를 지운다")
    void face_삭제_후_닫는다() {
        FeatureHistory row = 행(FeatureType.FACE);
        given(repository.featureExists(any())).willReturn(false);

        assertThat(reconciler.reconcileOne(row)).isTrue();

        ArgumentCaptor<DeleteFaceFeignRequestDTO> req = ArgumentCaptor.forClass(DeleteFaceFeignRequestDTO.class);
        verify(faceService).deleteFace(req.capture());
        assertThat(req.getValue().getFaceId()).as("지우는 것은 발급 id 뿐이다").isEqualTo(발급);
        assertThat(req.getValue().getBranchName()).isEqualTo("branch-A");
        assertThat(req.getValue().getClientId()).isEqualTo("9");
        verify(historyRecorder).fail(row);
        assertThat(row.getFeatureId()).isNull();
        assertThat(row.getFailureType()).isEqualTo("INTERNAL_SERVER_ERROR");
    }

    @Test
    @DisplayName("palm 행은 palm 에서 지운다")
    void palm_은_palm_으로() {
        FeatureHistory row = 행(FeatureType.PALM);
        given(repository.featureExists(any())).willReturn(false);

        assertThat(reconciler.reconcileOne(row)).isTrue();

        ArgumentCaptor<DeletePalmFeignRequestDTO> req = ArgumentCaptor.forClass(DeletePalmFeignRequestDTO.class);
        verify(palmService).deletePalm(req.capture());
        assertThat(req.getValue().getPalmId()).isEqualTo(발급);
        verify(faceService, never()).deleteFace(any());
    }

    /** 원격 등록이 닿지 않았거나 이미 지워졌다 — 되돌릴 것이 없다. 닫지 않으면 24시간 동안 매번 다시 온다. */
    @Test
    @DisplayName("하위가 '없음' 을 주면 되돌릴 것이 없으니 닫는다")
    void 없음이면_닫는다() {
        FeatureHistory row = 행(FeatureType.FACE);
        given(repository.featureExists(any())).willReturn(false);
        willThrow(new CustomFeignException("MATCH-004", "INVALID_FACE_ID", "no such face"))
                .given(faceService).deleteFace(any());

        assertThat(reconciler.reconcileOne(row)).isTrue();
        verify(historyRecorder).fail(row);
    }

    /** <b>확인하지 못하면 닫지 않는다.</b> 닫으면 id 가 지워져, 하위에 고아가 있어도 다시는 못 찾는다. */
    @Test
    @DisplayName("다른 하위 오류면 닫지 않고 남긴다 — 다음 실행에서 재시도한다")
    void 다른_오류면_남긴다() {
        FeatureHistory row = 행(FeatureType.FACE);
        given(repository.featureExists(any())).willReturn(false);
        willThrow(new CustomFeignException("SWAGGER-005", "INTERNAL_SERVER_ERROR", "match down"))
                .given(faceService).deleteFace(any());

        assertThat(reconciler.reconcileOne(row)).isFalse();
        verifyNoInteractions(historyRecorder);
        assertThat(row.getFeatureId()).isEqualTo(발급);
    }

    @Test
    @DisplayName("응답이 없어도(런타임 예외) 닫지 않는다")
    void 응답_없음도_남긴다() {
        FeatureHistory row = 행(FeatureType.FACE);
        given(repository.featureExists(any())).willReturn(false);
        willThrow(new IllegalStateException("connection refused")).given(faceService).deleteFace(any());

        assertThat(reconciler.reconcileOne(row)).isFalse();
        verifyNoInteractions(historyRecorder);
    }

    /** 한 행이 터져도 나머지는 계속 처리한다. */
    @Test
    @DisplayName("실행 한 번에 여러 행을 처리하고, 실패한 행이 다음 행을 막지 않는다")
    void 여러_행() {
        켠다();
        FeatureHistory 실패할 = 행(FeatureType.FACE);
        FeatureHistory 성공할 = 행(FeatureType.PALM);
        ReflectionTestUtils.setField(실패할, "id", 1L);
        ReflectionTestUtils.setField(성공할, "id", 2L);
        given(repository.findStaleRegistrations(any(), any(), anyLong(), anyInt())).willReturn(List.of(실패할, 성공할));
        given(repository.featureExists(any())).willReturn(false);
        willThrow(new IllegalStateException("down")).given(faceService).deleteFace(any());

        reconciler.reconcile();

        verify(palmService).deletePalm(any());
        verify(historyRecorder).fail(성공할);
        verify(historyRecorder, never()).fail(실패할);
    }
    /**
     * 브랜치가 없으면 match 는 {@code EMPTY_GALLERY} 를 준다 — 새 프로젝트의 첫 등록이 match 에 닿지 않은
     * 경우다. 이것도 "없음" 이다. 아니면 그 행은 24시간 동안 매번 다시 온다 (UG-338 반박 리뷰).
     */
    @Test
    @DisplayName("브랜치가 없다(EMPTY_GALLERY)는 답도 없음으로 보고 닫는다")
    void 브랜치_없음이면_닫는다() {
        FeatureHistory row = 행(FeatureType.FACE);
        given(repository.featureExists(any())).willReturn(false);
        willThrow(new CustomFeignException("MATCH-001", "EMPTY_GALLERY", "no branch"))
                .given(faceService).deleteFace(any());

        assertThat(reconciler.reconcileOne(row)).isTrue();
        verify(historyRecorder).fail(row);
    }

    /**
     * <b>커서로 한 바퀴씩 돈다</b> (UG-338 반박 리뷰). 매번 앞에서부터 집으면 닫히지 않는 행이 상한만큼 쌓였을
     * 때 뒤의 고아에 차례가 오지 않는다. 페이지가 꽉 차면 시간 한도 안에서 다음 페이지를 이어 읽는다
     * (2차 리뷰) — 한 실행에 한 페이지면 장애 뒤 후보가 수만 행일 때 한 바퀴가 24시간을 넘긴다.
     */
    @Test
    @DisplayName("페이지가 꽉 차면 같은 실행에서 마지막 id 뒤를 이어 읽고, 모자라면 다음 실행은 처음부터 본다")
    void 커서() {
        켠다();
        given(repository.featureExists(any())).willReturn(true); // 전부 남는 행 — 닫히지 않아도 커서는 나아간다
        given(repository.findStaleRegistrations(any(), any(), eq(0L), anyInt()))
                .willReturn(행들(1, OrphanRegistrationReconciler.실행당_상한));
        given(repository.findStaleRegistrations(any(), any(), eq(50L), anyInt()))
                .willReturn(행들(51, 3));

        reconciler.reconcile();
        verify(repository).findStaleRegistrations(any(), any(), eq(0L), eq(OrphanRegistrationReconciler.실행당_상한));
        verify(repository).findStaleRegistrations(any(), any(), eq(50L), eq(OrphanRegistrationReconciler.실행당_상한));
        verify(repository, times(53)).featureExists(any());
        assertThat(ReflectionTestUtils.getField(reconciler, "cursor")).as("한 바퀴를 다 돌면 되감는다").isEqualTo(0L);

        reconciler.reconcile();
        verify(repository, times(2)).findStaleRegistrations(any(), any(), eq(0L), anyInt());
    }

    /** 시간 한도로 멈추면 다음 실행은 멈춘 곳에서 이어 간다 — 처음으로 돌아가면 뒤의 행에 차례가 오지 않는다. */
    @Test
    @DisplayName("시간 한도로 멈춘 다음 실행은 멈춘 곳 뒤부터 읽는다")
    void 멈춘_곳에서_잇는다() {
        켠다();
        ReflectionTestUtils.setField(reconciler, "clock", 흐르는_시계(Duration.ofSeconds(50)));
        given(repository.featureExists(any())).willReturn(true);
        given(repository.findStaleRegistrations(any(), any(), eq(0L), anyInt())).willReturn(행들(1, 10));
        given(repository.findStaleRegistrations(any(), any(), eq(2L), anyInt())).willReturn(List.of());

        reconciler.reconcile();
        reconciler.reconcile();

        verify(repository).findStaleRegistrations(any(), any(), eq(2L), anyInt());
    }

    /** 존재 확인이나 닫기에서 DB 예외가 나도 그 실행의 나머지 행은 처리한다. */
    @Test
    @DisplayName("한 행의 DB 예외가 같은 실행의 다음 행을 막지 않는다")
    void DB_예외도_다음_행을_막지_않는다() {
        켠다();
        FeatureHistory 터질 = 행(1L);
        FeatureHistory 다음 = 행(2L);
        ReflectionTestUtils.setField(다음, "featureId", "11111111-1111-4111-8111-111111111111");
        given(repository.findStaleRegistrations(any(), any(), anyLong(), anyInt())).willReturn(List.of(터질, 다음));
        given(repository.featureExists(발급)).willThrow(new IllegalStateException("db"));
        given(repository.featureExists("11111111-1111-4111-8111-111111111111")).willReturn(false);

        reconciler.reconcile();

        verify(historyRecorder).fail(다음);
        verify(historyRecorder, never()).fail(터질);
    }

    /** 스케줄러 스레드를 퍼지 잡과 나눠 쓴다 — 하위가 느려도 한 실행이 스레드를 오래 쥐지 않는다. */
    @Test
    @DisplayName("실행당 시간 한도를 넘으면 남은 행은 다음 실행으로 넘기고, 커서는 멈춘 곳에 둔다")
    void 시간_한도() {
        켠다();
        ReflectionTestUtils.setField(reconciler, "clock", 흐르는_시계(Duration.ofSeconds(50)));
        given(repository.featureExists(any())).willReturn(true);
        given(repository.findStaleRegistrations(any(), any(), anyLong(), anyInt())).willReturn(행들(1, 10));

        reconciler.reconcile();

        // 시작 0초, 검사 50·100초는 통과, 150초에 멈춘다 — 두 행만 본다.
        verify(repository, times(2)).featureExists(any());
        assertThat(ReflectionTestUtils.getField(reconciler, "cursor")).isEqualTo(2L);
    }
}
