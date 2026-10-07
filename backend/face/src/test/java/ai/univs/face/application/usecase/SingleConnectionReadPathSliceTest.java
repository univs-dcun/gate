package ai.univs.face.application.usecase;

import ai.univs.face.application.input.IdentifyInput;
import ai.univs.face.application.input.VerifyByDescriptorInput;
import ai.univs.face.application.result.IdentifyResult;
import ai.univs.face.application.result.VerifyByDescriptorResult;
import ai.univs.face.application.service.ExtractService;
import ai.univs.face.application.service.FaceHistoryRecorder;
import ai.univs.face.application.service.SimilarityParser;
import ai.univs.face.domain.FaceHistory;
import ai.univs.face.domain.FaceLiveness;
import ai.univs.face.domain.FaceMatch;
import ai.univs.face.infrastructure.feign.extract.ExtractFeign;
import ai.univs.face.infrastructure.feign.extract.dto.ExtractBodyFeignResponseDTO;
import ai.univs.face.infrastructure.feign.extract.dto.ExtractFeignResponseApi;
import ai.univs.face.infrastructure.feign.extract.dto.ExtractFeignResponseDTO;
import ai.univs.face.infrastructure.feign.extract.dto.LivenessBodyFeignResponseDTO;
import ai.univs.face.infrastructure.feign.match.MatchFeign;
import ai.univs.face.infrastructure.feign.match.dto.IdentifyFeignResponseDTO;
import ai.univs.face.infrastructure.feign.match.dto.VerifyFeignResponseDTO;
import ai.univs.face.infrastructure.repository.FaceHistoryRepositoryImpl;
import ai.univs.face.infrastructure.repository.FaceMatchRepositoryImpl;
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
 * 커넥션 풀이 <b>하나뿐일 때</b> 읽기 경로가 끝까지 돌고, 원격 호출 동안 커넥션을 쥐지 않는가 (UG-358 1단계).
 *
 * <p>단위 테스트는 호출 순서만 본다. 이 클래스는 티켓의 주장 자체를 실제 풀로 잰다 — gate UG-336 의
 * {@code SingleConnectionSliceTest} 와 같은 방식이다.
 * <ul>
 *   <li>원격 호출 목(fxp 추출·match) 안에서 {@code isActualTransactionActive()} 가 false 인가 (티켓 완료 기준)
 *   <li>그 안에서 풀의 유일한 커넥션을 <b>빌릴 수 있는가</b> — 호출자가 쥐고 있으면 못 빌린다
 *   <li>풀이 하나여도 끝까지 도는가 — 요청이 커넥션 둘을 동시에 요구하면 {@code connectionTimeout} 뒤 실패한다
 * </ul>
 *
 * <p>트랜잭션 없이 돈다({@code NOT_SUPPORTED}). {@code @DataJpaTest} 의 기본 테스트 트랜잭션은 그 자체로 커넥션을
 * 쥐어 검증하려는 조건을 망가뜨린다. DB 는 인메모리 H2 이고 스키마는 엔티티에서 만든다(Flyway 끔) — 방언이 아니라
 * 커넥션 수를 보는 테스트다.
 */
@DataJpaTest(properties = {
        "spring.cloud.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:face-ug358;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.datasource.hikari.minimum-idle=1",
        // Hikari 하한이 250ms 다. 정상 경로는 기다릴 일이 없으므로 1초면 느린 CI 에서도 오판하지 않는다.
        "spring.datasource.hikari.connection-timeout=1000",
        "face.match.threshold=0.85"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({IdentifyUseCase.class, VerifyByDescriptorUseCase.class, FaceHistoryRecorder.class, ExtractService.class,
        SimilarityParser.class, FaceHistoryRepositoryImpl.class, FaceMatchRepositoryImpl.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("UG-358: 풀 크기 1 에서 읽기 경로")
class SingleConnectionReadPathSliceTest {

    private static final String CLIENT = "client-ug358";

    @MockBean private MatchFeign matchFeign;
    @MockBean private ExtractFeign extractFeign;
    @MockBean private MessageService messageService;

    @Autowired private IdentifyUseCase identifyUseCase;
    @Autowired private VerifyByDescriptorUseCase verifyByDescriptorUseCase;
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

    private long 행수(Class<?> entity, String transactionUuid) {
        return tx.execute(status -> em.createQuery(
                        "SELECT COUNT(e) FROM " + entity.getSimpleName() + " e WHERE e.faceHistory.transactionUuid = :t",
                        Long.class)
                .setParameter("t", transactionUuid)
                .getSingleResult());
    }

    @Test
    @DisplayName("전제: 데이터소스는 크기 1 인 Hikari 풀이다")
    void 전제_풀_크기_1() {
        assertThat(dataSource).isInstanceOf(HikariDataSource.class);
        assertThat(((HikariDataSource) dataSource).getMaximumPoolSize()).isEqualTo(1);
    }

    /**
     * <b>대조군</b> — 예전 구조(트랜잭션 안에서 원격 호출)는 원격 호출 중 커넥션을 빌리지 못한다.
     * 이것이 「못빌림」이 아니면 아래 테스트의 「빌림」은 아무것도 증명하지 못한다.
     */
    @Test
    @DisplayName("대조군: 트랜잭션 안에서 원격 호출하면 그 안에서 커넥션을 빌리지 못한다")
    void 대조군_트랜잭션_안의_원격_호출() {
        tx.executeWithoutResult(status -> {
            em.createQuery("SELECT COUNT(h) FROM FaceHistory h").getSingleResult();   // 유일한 커넥션 확보
            원격_호출_관찰("예전구조");
        });

        assertThat(원격_관찰).containsExactly("예전구조:트랜잭션=true,커넥션=못빌림");
    }

    @Test
    @DisplayName("1:N(이미지, 라이브니스 켬) 성공 — fxp·match 호출 중 트랜잭션 없음·커넥션 빌림, 이력·결과·라이브니스 행이 남는다")
    void 이미지_1N_성공() {
        fxp_추출_성공();
        given(matchFeign.identify(any())).willAnswer(i -> {
            원격_호출_관찰("match");
            return new FeignResponseApi<>(true, new IdentifyFeignResponseDTO("face-ug358", "0.93"), null);
        });
        String txn = UUID.randomUUID().toString();

        IdentifyResult result = identifyUseCase.execute(new IdentifyInput(
                "branch-ug358", new MockMultipartFile("image", new byte[] {1}), txn, CLIENT, true, true));

        assertThat(result.result()).isTrue();
        assertThat(원격_관찰).containsExactly(
                "fxp:트랜잭션=false,커넥션=빌림",
                "match:트랜잭션=false,커넥션=빌림");

        FaceHistory history = 이력(txn);
        assertThat(history.isResult()).isTrue();
        assertThat(history.getFailureMessage()).isNull();
        assertThat(행수(FaceMatch.class, txn)).isEqualTo(1);
        assertThat(행수(FaceLiveness.class, txn)).isEqualTo(1);
    }

    /**
     * 예전에는 {@code UpstreamCallException} 이 noRollbackFor 에 없어 이력 행이 통째로 롤백됐다. 이제 시작이 먼저
     * 커밋되므로 행이 남고, 사유가 비지 않는다(UG-280 교훈).
     */
    @Test
    @DisplayName("match 5xx — 이력 행이 남고 사유는 INTERNAL_SERVER_ERROR (예전에는 행이 롤백으로 사라졌다)")
    void match_5xx_이력이_남는다() {
        fxp_추출_성공();
        given(matchFeign.identify(any())).willAnswer(i -> {
            원격_호출_관찰("match");
            throw new UpstreamCallException(503, "MatchFeign#identify", "Service Unavailable");
        });
        String txn = UUID.randomUUID().toString();

        assertThatThrownBy(() -> identifyUseCase.execute(new IdentifyInput(
                "branch-ug358", new MockMultipartFile("image", new byte[] {1}), txn, CLIENT, false, false)))
                .isInstanceOf(UpstreamCallException.class);

        assertThat(원격_관찰).containsExactly(
                "fxp:트랜잭션=false,커넥션=빌림",
                "match:트랜잭션=false,커넥션=빌림");
        FaceHistory history = 이력(txn);
        assertThat(history.isResult()).isFalse();
        assertThat(history.getFailureMessage()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(행수(FaceMatch.class, txn)).isZero();
    }

    @Test
    @DisplayName("1:1(특징점) 임계치 미달 — 결과 행과 NOT_MATCH 가 함께 커밋된다")
    void 특징점_1대1_미달() {
        given(matchFeign.verifyByDescriptor(any())).willAnswer(i -> {
            원격_호출_관찰("match");
            return new FeignResponseApi<>(true, new VerifyFeignResponseDTO("0.30"), null);
        });
        String txn = UUID.randomUUID().toString();

        VerifyByDescriptorResult result = verifyByDescriptorUseCase.execute(
                new VerifyByDescriptorInput("d1", "d2", txn, CLIENT));

        assertThat(result.result()).isFalse();
        assertThat(원격_관찰).containsExactly("match:트랜잭션=false,커넥션=빌림");
        FaceHistory history = 이력(txn);
        assertThat(history.getFailureMessage()).isEqualTo("NOT_MATCH");
        assertThat(행수(FaceMatch.class, txn)).isEqualTo(1);
    }
}
