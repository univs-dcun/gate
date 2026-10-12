package ai.univs.match.application.service;

import ai.univs.match.application.input.DescriptorDetail;
import ai.univs.match.domain.entity.Branch;
import ai.univs.match.domain.entity.Descriptor;
import ai.univs.match.infrastructure.persistence.BranchRepository;
import ai.univs.match.infrastructure.persistence.DescriptorRepository;
import ai.univs.match.infrastructure.persistence.UniqueViolation;
import ai.univs.match.shared.exception.CustomFaceMatcherException;
import ai.univs.match.shared.web.enums.ErrorType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 특징점 등록.
 *
 * <p><b>트랜잭션 경계 (UG-372).</b> 브랜치를 정하는 일과 특징점을 저장하는 일은 서로 다른 트랜잭션이다.
 * <ol>
 *   <li>형식 검사 — DB 를 건드리지 않는다. 깨진 특징점으로 빈 브랜치가 생기지 않게 가장 먼저 한다.
 *   <li>{@link BranchProvisioner#findOrCreate} — 트랜잭션 밖에서 부른다. 새 브랜치는 자기 트랜잭션에서 커밋되고, 같은 이름을
 *       동시에 만들다 지면 이긴 쪽 브랜치를 다시 읽는다. UG-340 에서는 이 INSERT 가 등록 트랜잭션 안에 있어, 진 쪽은
 *       (PostgreSQL 에서 트랜잭션이 중단돼) 다시 읽지 못하고 서버 오류로 끝났다 — 특징점은 저장되지 않았는데 gate 는
 *       "결과를 모른다"(400 PJ-005)로 냈다. 이제 진 쪽도 등록에 성공한다.
 *   <li>등록 트랜잭션 — 아래 {@code registerTransaction}. 브랜치는 id 로만 참조한다.
 * </ol>
 * 그래서 호출자(유스케이스)는 {@code @Transactional} 을 두지 않는다. 두면 2단계가 트랜잭션 안에서 불려 바로 실패한다 — 이유는
 * {@link BranchProvisioner} 의 「커넥션 풀」. 요청 하나가 커넥션을 동시에 둘 쥐는 일은 없다.
 *
 * <p>대가 두 가지. ① 브랜치가 이미 있어도 조회가 짧은 읽기 전용 트랜잭션으로 따로 나간다 — SELECT 수는 예전과 같고,
 * 커넥션을 한 번 더 빌렸다 돌려준다(동시에 둘이 아니라 차례로). ② 새 브랜치의 첫 등록이 브랜치 커밋 뒤에 실패하면(이미
 * 등록됨·풀 고갈 등) 빈 브랜치가 남는다. 마지막 특징점을 지운 브랜치와 같은 상태라 다음 등록이 그대로 쓴다.
 */
@Slf4j
@Component
public class RegisterService {

    private final BranchProvisioner branchProvisioner;
    private final BranchRepository branchRepository;
    private final DescriptorRepository descriptorRepository;
    private final DuplicateService duplicateService;
    private final TransactionTemplate registerTransaction;

    public RegisterService(BranchProvisioner branchProvisioner,
                           BranchRepository branchRepository,
                           DescriptorRepository descriptorRepository,
                           DuplicateService duplicateService,
                           PlatformTransactionManager transactionManager
    ) {
        this.branchProvisioner = branchProvisioner;
        this.branchRepository = branchRepository;
        this.descriptorRepository = descriptorRepository;
        this.duplicateService = duplicateService;
        this.registerTransaction = new TransactionTemplate(transactionManager);
    }

    /** 트랜잭션 밖에서 부른다 — 위 「트랜잭션 경계」. */
    public void register(String branchName,
                         String faceId,
                         String faceDescriptor
    ) {
        var descriptorDetail = DescriptorDetail.from(faceDescriptor);

        Long branchId = branchProvisioner.findOrCreate(branchName).getId();

        registerTransaction.executeWithoutResult(status -> save(branchId, faceId, descriptorDetail));
    }

    private void save(Long branchId, String faceId, DescriptorDetail descriptorDetail) {
        // 브랜치는 다른 트랜잭션에서 읽었다 — 이 영속성 컨텍스트에는 없을 수 있다(분리 상태). 분리된 엔티티를 그대로 물리면
        // 지금은 동작해도 cascade 가 붙는 순간 "detached entity passed to persist" 가 된다. id 로 참조만 얻는다 — SELECT 도
        // INSERT 도 나가지 않고, 이미 컨텍스트에 있으면(OSIV) 그 인스턴스가 온다.
        Branch branch = branchRepository.getReferenceById(branchId);

        // 브랜치(특징점을 보관할 구분 키) 키, faceId 를 이미 사용하고 있는 유저가 있는지 확인합니다.
        // 방금 만든 브랜치여도 확인한다 — 경합에서 진 요청에게는 이긴 쪽 특징점이 이미 있을 수 있다 (UG-372).
        if (descriptorRepository.findByFaceIdAndBranch(faceId, branch).isPresent()) {
            throw new CustomFaceMatcherException(ErrorType.ALREADY_REGISTERED_DESCRIPTOR);
        }

        // 브렌치에 등록된 사용자가 한 명이라도 있는지 확인합니다. 세지 않고 있는지만 본다 (UG-357)
        if (descriptorRepository.existsByBranch(branch)) {
            // 이미 등록된 사용자들 중 동일한 사용자가 있는지 확인합니다.
            duplicateService.checkDuplicateDescriptor(branch, descriptorDetail, null, false);
        }

        Descriptor descriptor = Descriptor.builder()
                .branch(branch)
                .faceId(faceId)
                .descriptor(descriptorDetail.descriptor())
                .descriptorType(descriptorDetail.descriptorType())
                .descriptorBody(descriptorDetail.descriptorBody())
                .descriptorVersion(descriptorDetail.descriptorSpec().getVersion())
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .modifiedAt(LocalDateTime.now(ZoneOffset.UTC))
                .build();
        try {
            // 위의 사전 조회는 빠른 경로다. 동시 요청 둘이 함께 통과할 수 있어 최종 판정은 제약이 한다 (UG-340).
            // 위반을 이 자리에서 받아 바꾸려면 INSERT 가 여기서 나가야 한다. 지금은 IDENTITY 라 save 도 즉시
            // INSERT 하지만, id 전략이 바뀌면 커밋 시점으로 밀려 catch 를 빠져나간다 — flush 로 명시한다.
            descriptorRepository.saveAndFlush(descriptor);
        } catch (DataIntegrityViolationException e) {
            if (!UniqueViolation.of(e, UniqueViolation.DESCRIPTOR_BRANCH_FACE)) {
                throw e;
            }
            throw new CustomFaceMatcherException(ErrorType.ALREADY_REGISTERED_DESCRIPTOR);
        }
    }
}
