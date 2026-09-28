package ai.univs.match.application.service;

import ai.univs.match.application.input.DescriptorDetail;
import ai.univs.match.domain.entity.Branch;
import ai.univs.match.domain.entity.Descriptor;
import ai.univs.match.infrastructure.persistence.BranchRepository;
import ai.univs.match.infrastructure.persistence.DescriptorRepository;
import ai.univs.match.infrastructure.persistence.UniqueViolation;
import ai.univs.match.shared.exception.CustomFaceMatcherException;
import ai.univs.match.shared.web.enums.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RegisterService {

    private final BranchRepository branchRepository;
    private final DescriptorRepository descriptorRepository;
    private final DuplicateService duplicateService;

    public void register(String branchName,
                         String faceId,
                         String faceDescriptor
    ) {
        var descriptorDetail = DescriptorDetail.from(faceDescriptor);

        Optional<Branch> OpBranch = branchRepository.findByBranchName(branchName);
        Branch branch;
        if (OpBranch.isPresent()) {
            branch = OpBranch.get();

            // 브랜치(특징점을 보관할 구분 키) 키, faceId 를 이미 사용하고 있는 유저가 있는지 확인합니다.
            if (descriptorRepository.findByFaceIdAndBranch(faceId, branch).isPresent()) {
                throw new CustomFaceMatcherException(ErrorType.ALREADY_REGISTERED_DESCRIPTOR);
            }

            // 브렌치에 등록된 사용자가 한 명이라도 있는지 확인합니다.
            if (descriptorRepository.countByBranch(branch) > 0) {
                // 이미 등록된 사용자들 중 동일한 사용자가 있는지 확인합니다.
                duplicateService.checkDuplicateDescriptor(branch, descriptorDetail, null, false);
            }
        } else {
            branch = Branch.builder()
                    .branchName(branchName)
                    .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                    .modifiedAt(LocalDateTime.now(ZoneOffset.UTC))
                    .build();
            try {
                branchRepository.saveAndFlush(branch);
            } catch (DataIntegrityViolationException e) {
                if (!UniqueViolation.of(e, UniqueViolation.BRANCH_NAME)) {
                    throw e;
                }
                // UG-340: 새 브랜치의 첫 등록 두 건이 동시에 왔다 — 다른 쪽이 먼저 만들었다. 이 트랜잭션은
                // 이미 오류 상태라(PostgreSQL) 그 브랜치를 다시 읽어 이어 갈 수 없다. 재시도하면 브랜치가
                // 있으므로 정상 경로를 탄다. 서버 오류로 돌려 호출자(gate)가 "결과를 모른다" 로 다루게 한다.
                log.warn("브랜치 동시 생성 경합 — 재시도하면 된다. branchName={}", branchName);
                throw new CustomFaceMatcherException(ErrorType.INTERNAL_SERVER_ERROR);
            }
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
