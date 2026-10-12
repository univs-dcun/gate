package ai.univs.match.application.service;

import ai.univs.match.domain.entity.Branch;
import ai.univs.match.infrastructure.persistence.BranchRepository;
import ai.univs.match.infrastructure.persistence.UniqueViolation;
import ai.univs.match.shared.exception.CustomFaceMatcherException;
import ai.univs.match.shared.web.enums.ErrorType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * 브랜치를 찾고, 없으면 <b>자기 트랜잭션에서</b> 만든다. 같은 이름을 동시에 만들다 지면 이긴 쪽이 커밋한 브랜치를 다시 읽어
 * 돌려준다 (UG-372).
 *
 * <p><b>왜 따로 떼었나.</b> 예전에는 등록 트랜잭션 안에서 브랜치를 INSERT 했다. 새 프로젝트의 첫 등록이 동시에 오면 하나만
 * 이기고 나머지는 {@code uk_branch_branch_name}(V5) 위반을 받는데, PostgreSQL 은 그 순간 트랜잭션 전체를 중단 상태로
 * 만든다 — 이후 문장은 모두 {@code 25P02} 로 거절되고, COMMIT 은 조용히 ROLLBACK 이 된다. 그래서 진 쪽은 브랜치를 다시
 * 읽을 수 없어 서버 오류로 끝냈고(UG-340), gate 는 그것을 "결과를 모른다"(400 PJ-005)로 냈다. 특징점은 저장되지 않았는데도.
 * scaling(134) 에서 새 프로젝트에 16건 동시 등록 중 9건이 이렇게 실패했다. 이제 브랜치 INSERT 는 자기만의 짧은 트랜잭션에서
 * 나가므로, 실패해도 버려지는 것은 그 트랜잭션뿐이다. 다시 읽기는 그다음 새 트랜잭션에서 한다.
 *
 * <p><b>다시 읽으면 반드시 보이는가.</b> PostgreSQL·Oracle 모두 아직 커밋되지 않은 같은 키가 있으면 INSERT 가 그 트랜잭션이
 * 끝날 때까지 기다린다. 상대가 커밋하면 위반, 롤백하면 우리 INSERT 가 성공한다. 즉 위반을 받았다면 상대는 이미 커밋했고,
 * READ COMMITTED 인 새 트랜잭션에서 그 행이 보인다. 브랜치는 지우는 경로가 없다 — 그래도 못 찾으면 예전처럼 서버 오류다.
 *
 * <p><b>트랜잭션 밖에서만 부른다 — 커넥션 풀 때문이다.</b> 이 메서드는 커넥션을 한 번에 하나만 쓴다: 조회(저장소의 읽기 전용
 * 트랜잭션) → 생성(아래 {@code REQUIRES_NEW}) → 경합에서 졌으면 다시 조회. OSIV 가 꺼져 있으면 각각 빌렸다 돌려주고,
 * 운영처럼 켜져 있으면 요청 범위 세션이 쥔 커넥션 하나를 이어 쓴다({@link RegisterService} 「대가 ①」). 만약 바깥 트랜잭션 안에서
 * 부르면 {@code REQUIRES_NEW} 는 바깥 커넥션을 쥔 채 커넥션을 하나 더 빌린다. 이 서비스는 트랜잭션을 시작할 때 커넥션을
 * 바로 잡으므로(Hikari autoCommit 기본값 → Hibernate 가 begin 에서 획득) 바깥이 아직 DB 를 안 건드렸어도 마찬가지다. 풀은
 * 10, 대기 상한은 1.5초다(UG-359). 첫 등록 10건이 동시에 오면 10개가 바깥 커넥션을 다 쥐고 서로의 두 번째 커넥션을 기다리다
 * 1.5초 뒤 모두 503 으로 끝난다 — 고치려던 장애를 다른 이름으로 다시 만든다. 그래서 등록 트랜잭션은 이 메서드가 끝난 뒤에
 * 열고({@link RegisterService}), 트랜잭션 안에서 불리면 바로 실패시켜 구조가 되돌아가는 것을 테스트가 잡게 한다.
 * {@code REQUIRES_NEW} 는 그 전제에서 {@code REQUIRED} 와 같게 동작한다 — "이 INSERT 는 어떤 트랜잭션과도 섞이지 않는다" 는
 * 의도를 코드에 남기려고 둔다.
 *
 * <p><b>템플릿을 쓰는 이유.</b> {@code @Transactional(REQUIRES_NEW)} 메서드를 같은 클래스에서 부르면 프록시를 거치지 않아
 * 조용히 무시된다. 생성과 "잡고 다시 읽기" 를 한 클래스에 두면서 경계를 확실히 하려고 {@link TransactionTemplate} 으로 연다.
 *
 * <p>Oracle 은 제약 위반에서 트랜잭션을 중단시키지 않는다(문장 단위 롤백). 같은 경로로 문제없이 동작한다.
 *
 * <p><b>경합 ERROR 줄 — 장애가 아니다.</b> 진 쪽 INSERT 는 예외가 여기 오기 전에 Hibernate {@code SqlExceptionHelper} 가
 * ERROR 로 {@code duplicate key value violates unique constraint "uk_branch_branch_name"} 을 한 줄 남긴다(PostgreSQL 문구,
 * 바로 앞에 WARN {@code SQLState: 23505} 줄). 요청은 위 「다시 읽기」 로 성공하고, 뒤이어 INFO 「브랜치 동시 생성 경합」 이
 * 찍힌다. 새 브랜치의 첫 등록이 동시에 몰릴 때만 나며, 진 요청 수만큼이다. 수용했다 — 없애려면 PostgreSQL
 * {@code INSERT ... ON CONFLICT DO NOTHING}·Oracle {@code MERGE} 같은 벤더별 SQL 을 따로 둬야 해서 하지 않았다. 당직은 이 줄을
 * 실패로 다루지 않는다 — 같은 요청에 INFO 경합 줄이 뒤따르는지, 이어서 등록 실패가 없는지만 본다.
 */
@Slf4j
@Component
public class BranchProvisioner {

    private final BranchRepository branchRepository;
    private final TransactionTemplate createTransaction;

    public BranchProvisioner(BranchRepository branchRepository, PlatformTransactionManager transactionManager) {
        this.branchRepository = branchRepository;
        this.createTransaction = new TransactionTemplate(transactionManager);
        this.createTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 이름으로 브랜치를 찾고, 없으면 만든다. 돌려주는 엔티티는 호출자의 영속성 컨텍스트 밖에서 읽었을 수 있다 — 등록
     * 트랜잭션에서는 id 로 참조를 다시 얻어 쓴다({@code getReferenceById}).
     *
     * @throws IllegalStateException 트랜잭션 안에서 불렸다 (위 「커넥션 풀」 참고)
     */
    public Branch findOrCreate(String branchName) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "브랜치 생성은 트랜잭션 밖에서 해야 한다 — 안에서 하면 커넥션을 둘 쥐어 풀이 교착될 수 있다 (UG-372)");
        }

        Optional<Branch> found = branchRepository.findByBranchName(branchName);
        if (found.isPresent()) {
            return found.get();
        }

        try {
            return createTransaction.execute(status -> branchRepository.saveAndFlush(newBranch(branchName)));
        } catch (DataIntegrityViolationException e) {
            if (!UniqueViolation.of(e, UniqueViolation.BRANCH_NAME)) {
                throw e;
            }
            // 같은 이름을 다른 요청이 먼저 만들고 커밋했다. 실패한 INSERT 의 트랜잭션은 이미 롤백됐고, 여기는 새 트랜잭션이다.
            log.info("브랜치 동시 생성 경합 — 먼저 만든 브랜치를 다시 읽어 이어 간다. branchName={}", branchName);
            return branchRepository.findByBranchName(branchName).orElseThrow(() -> {
                log.error("브랜치 이름 위반을 받았는데 다시 읽으니 없다. branchName={}", branchName, e);
                return new CustomFaceMatcherException(ErrorType.INTERNAL_SERVER_ERROR);
            });
        }
    }

    private static Branch newBranch(String branchName) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return Branch.builder()
                .branchName(branchName)
                .createdAt(now)
                .modifiedAt(now)
                .build();
    }
}
