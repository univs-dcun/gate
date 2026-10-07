package ai.univs.face.application.usecase;

import ai.univs.face.application.input.DeleteInput;
import ai.univs.face.application.input.RegisterInput;
import ai.univs.face.application.result.DeleteResult;
import ai.univs.face.application.result.RegisterResult;
import ai.univs.face.application.service.ExtractService;
import ai.univs.face.application.service.FaceHistoryRecorder;
import ai.univs.face.domain.ActionType;
import ai.univs.face.domain.FaceHistory;
import ai.univs.face.infrastructure.feign.extract.ExtractFeign;
import ai.univs.face.infrastructure.feign.extract.dto.ExtractBodyFeignResponseDTO;
import ai.univs.face.infrastructure.feign.extract.dto.ExtractFeignResponseApi;
import ai.univs.face.infrastructure.feign.extract.dto.ExtractFeignResponseDTO;
import ai.univs.face.infrastructure.feign.extract.dto.LivenessBodyFeignResponseDTO;
import ai.univs.face.infrastructure.feign.match.MatchFeign;
import ai.univs.face.infrastructure.feign.match.dto.MatchFeignResponseDTO;
import ai.univs.face.infrastructure.repository.FaceHistoryRepositoryImpl;
import ai.univs.face.infrastructure.repository.FaceMatchRepositoryImpl;
import ai.univs.face.shared.exception.CustomFeignException;
import ai.univs.face.shared.exception.InvalidFaceModuleException;
import ai.univs.face.shared.exception.UpstreamCallException;
import ai.univs.face.shared.feign.dto.FeignResponseApi;
import ai.univs.face.shared.locale.MessageService;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;

/**
 * 커넥션 풀이 <b>하나뿐일 때</b> 쓰기 경로(등록·삭제)가 끝까지 돌고, 원격 호출 동안 커넥션을 쥐지 않는가 (UG-358 2단계).
 *
 * <p>{@code SingleConnectionReadPathSliceTest}(1단계)와 같은 방식이다. 대조군(예전 구조는 원격 호출 중 커넥션을 빌리지
 * 못한다)은 그쪽에 있다 — 여기의 「빌림」도 그 대조군에 기대어 의미가 있다.
 * <ul>
 *   <li>원격 호출 목(fxp 추출·match 등록/삭제) 안에서 {@code isActualTransactionActive()} 가 false 인가
 *   <li>그 안에서 풀의 유일한 커넥션을 <b>빌릴 수 있는가</b>
 *   <li>DB 의 최종 행이 기대한 상태인가 — 특히 match 5xx 에서 이력·라이브니스 행이 남는가(예전에는 롤백으로 사라졌다)
 * </ul>
 */
@DataJpaTest(properties = {
        "spring.cloud.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:face-ug358-write;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.datasource.hikari.minimum-idle=1",
        // Hikari 하한이 250ms 다. 정상 경로는 기다릴 일이 없으므로 1초면 느린 CI 에서도 오판하지 않는다.
        "spring.datasource.hikari.connection-timeout=1000",
        "face.match.threshold=0.85"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({RegisterUseCase.class, DeleteUseCase.class, FaceHistoryRecorder.class, ExtractService.class,
        FaceHistoryRepositoryImpl.class, FaceMatchRepositoryImpl.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("UG-358: 풀 크기 1 에서 쓰기 경로")
class SingleConnectionWritePathSliceTest {

    private static final String CLIENT = "client-ug358";

    @MockBean private MatchFeign matchFeign;
    @MockBean private ExtractFeign extractFeign;
    @MockBean private MessageService messageService;

    @Autowired private RegisterUseCase registerUseCase;
    @Autowired private DeleteUseCase deleteUseCase;
    @Autowired private DataSource dataSource;
    @Autowired private EntityManager em;
    @Autowired private TransactionTemplate tx;

    /** 원격 호출마다 남긴 관찰: 그 순간 트랜잭션이 있었는가, 커넥션을 빌렸는가. */
    private final List<String> 원격_관찰 = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        원격_관찰.clear();
    }

    /** 원격 호출 목의 본체 — 트랜잭션 여부를 적고, 풀에 남는 커넥션이 있는지 빌려 본다. */
    private void 원격_호출_관찰(String 이름) {
        boolean 트랜잭션 = TransactionSynchronizationManager.isActualTransactionActive();
        String 커넥션;
        try (Connection ignored = dataSource.getConnection()) {
            커넥션 = "빌림";
        } catch (Exception e) {
            커넥션 = "못빌림";
        }
        원격_관찰.add(이름 + ":트랜잭션=" + 트랜잭션 + ",커넥션=" + 커넥션);
    }

    private void fxp_추출_성공() {
        given(extractFeign.extractWithOptionalLivenessAndMultiFace(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any()))
                .willAnswer(i -> {
                    원격_호출_관찰("fxp");
                    return new ExtractFeignResponseApi<>("SUCCESS", "ok", new ExtractFeignResponseDTO(
                            new ExtractBodyFeignResponseDTO("", "descriptor-ug358"),
                            new LivenessBodyFeignResponseDTO("0.99", 0, "real", "good", "0.5"),
                            1));
                });
    }

    private FaceHistory 이력(String transactionUuid) {
        return tx.execute(status -> em.createQuery(
                        "SELECT h FROM FaceHistory h WHERE h.transactionUuid = :t", FaceHistory.class)
                .setParameter("t", transactionUuid)
                .getSingleResult());
    }

    private long 라이브니스_행수(String transactionUuid) {
        return tx.execute(status -> em.createQuery(
                        "SELECT COUNT(e) FROM FaceLiveness e WHERE e.faceHistory.transactionUuid = :t", Long.class)
                .setParameter("t", transactionUuid)
                .getSingleResult());
    }

    @Test
    @DisplayName("전제: 데이터소스는 크기 1 인 Hikari 풀이다")
    void 전제_풀_크기_1() {
        assertThat(dataSource).isInstanceOf(HikariDataSource.class);
        assertThat(((HikariDataSource) dataSource).getMaximumPoolSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("등록(이미지, 라이브니스 켬) 성공 — fxp·match 호출 중 트랜잭션 없음·커넥션 빌림, 이력에 매처가 준 faceId 가 남는다")
    void 이미지_등록_성공() {
        fxp_추출_성공();
        given(matchFeign.register(any())).willAnswer(i -> {
            원격_호출_관찰("match");
            return new FeignResponseApi<>(true, new MatchFeignResponseDTO("branch-ug358", "issued-ug358"), null);
        });
        String txn = UUID.randomUUID().toString();

        RegisterResult result = registerUseCase.execute(new RegisterInput(
                "", new MockMultipartFile("image", new byte[] {1}), "branch-ug358", txn, CLIENT, true, true));

        assertThat(result.faceId()).isEqualTo("issued-ug358");
        assertThat(원격_관찰).containsExactly(
                "fxp:트랜잭션=false,커넥션=빌림",
                "match:트랜잭션=false,커넥션=빌림");

        FaceHistory history = 이력(txn);
        assertThat(history.getType()).isEqualTo(ActionType.ADD);
        assertThat(history.isResult()).isTrue();
        assertThat(history.getFailureMessage()).isNull();
        assertThat(history.getFaceId()).isEqualTo("issued-ug358");
        assertThat(라이브니스_행수(txn)).isEqualTo(1);
    }

    /**
     * 예전에는 {@code UpstreamCallException} 이 noRollbackFor 에 없어 이력·라이브니스 행이 통째로 롤백됐다. 이제 둘 다
     * 남고 사유가 비지 않는다.
     */
    @Test
    @DisplayName("등록 중 match 5xx — 이력(INTERNAL_SERVER_ERROR)과 라이브니스 행이 남는다 (예전에는 롤백으로 사라졌다)")
    void 등록_match_5xx() {
        fxp_추출_성공();
        given(matchFeign.registerWithFaceId(any())).willAnswer(i -> {
            원격_호출_관찰("match");
            throw new UpstreamCallException(503, "MatchFeign#registerWithFaceId", "Service Unavailable");
        });
        String txn = UUID.randomUUID().toString();

        assertThatThrownBy(() -> registerUseCase.execute(new RegisterInput(
                "caller-ug358", new MockMultipartFile("image", new byte[] {1}), "branch-ug358", txn, CLIENT, true, true)))
                .isInstanceOf(UpstreamCallException.class);

        assertThat(원격_관찰).containsExactly(
                "fxp:트랜잭션=false,커넥션=빌림",
                "match:트랜잭션=false,커넥션=빌림");
        FaceHistory history = 이력(txn);
        assertThat(history.isResult()).isFalse();
        assertThat(history.getFailureMessage()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(history.getFaceId()).isEqualTo("caller-ug358");
        assertThat(라이브니스_행수(txn)).isEqualTo(1);
    }

    @Test
    @DisplayName("삭제 성공 — match 호출 중 트랜잭션 없음·커넥션 빌림, 이력은 REMOVE result=true")
    void 삭제_성공() {
        given(matchFeign.delete(any())).willAnswer(i -> {
            원격_호출_관찰("match");
            return new FeignResponseApi<>(true, new MatchFeignResponseDTO("branch-ug358", "face-ug358"), null);
        });
        String txn = UUID.randomUUID().toString();

        DeleteResult result = deleteUseCase.execute(new DeleteInput("branch-ug358", "face-ug358", txn, CLIENT));

        assertThat(result.faceId()).isEqualTo("face-ug358");
        assertThat(원격_관찰).containsExactly("match:트랜잭션=false,커넥션=빌림");
        FaceHistory history = 이력(txn);
        assertThat(history.getType()).isEqualTo(ActionType.REMOVE);
        assertThat(history.isResult()).isTrue();
        assertThat(history.getFailureMessage()).isNull();
        assertThat(history.getFaceId()).isEqualTo("face-ug358");
    }

    /**
     * 다시 지우면 match 가 {@code INVALID_FACE_ID} 로 답한다. face 는 그 유형을 그대로 전하고(gate 의
     * {@code DownstreamAbsence} 가 그것을 성공으로 받는다), 이력에는 그 사유가 남는다 — 예전과 같다.
     */
    @Test
    @DisplayName("삭제 — match 가 INVALID_FACE_ID 로 답하면 그 유형으로 실패하고 사유가 남는다")
    void 삭제_이미_없음() {
        given(matchFeign.delete(any())).willAnswer(i -> {
            원격_호출_관찰("match");
            throw new CustomFeignException("MATCH-004", "INVALID_FACE_ID", "없음");
        });
        String txn = UUID.randomUUID().toString();

        assertThatThrownBy(() -> deleteUseCase.execute(new DeleteInput("branch-ug358", "face-ug358", txn, CLIENT)))
                .isInstanceOf(InvalidFaceModuleException.class)
                .extracting(e -> ((InvalidFaceModuleException) e).getType())
                .isEqualTo("INVALID_FACE_ID");

        assertThat(원격_관찰).containsExactly("match:트랜잭션=false,커넥션=빌림");
        FaceHistory history = 이력(txn);
        assertThat(history.isResult()).isFalse();
        assertThat(history.getFailureMessage()).isEqualTo("INVALID_FACE_ID");
    }
}
