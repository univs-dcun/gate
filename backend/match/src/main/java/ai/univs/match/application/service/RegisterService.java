package ai.univs.match.application.service;

import ai.univs.match.application.input.DescriptorDetail;
import ai.univs.match.domain.entity.Branch;
import ai.univs.match.domain.entity.Descriptor;
import ai.univs.match.infrastructure.persistence.BranchRepository;
import ai.univs.match.infrastructure.persistence.DescriptorRepository;
import ai.univs.match.shared.exception.CustomFaceMatcherException;
import ai.univs.match.shared.web.enums.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
            branchRepository.save(branch);
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
        descriptorRepository.save(descriptor);
    }
}
