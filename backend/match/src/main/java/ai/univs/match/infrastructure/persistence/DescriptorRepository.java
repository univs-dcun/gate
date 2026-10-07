package ai.univs.match.infrastructure.persistence;

import ai.univs.match.domain.entity.Branch;
import ai.univs.match.domain.entity.Descriptor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DescriptorRepository extends JpaRepository<Descriptor, Long> {

    Optional<Descriptor> findByFaceIdAndBranch(String faceId, Branch branch);

    /**
     * 브랜치에 특징점이 하나라도 있는가 (UG-357).
     *
     * <p>예전에는 {@code countByBranch(...) > 0} 이었다. 0 인지만 보면 되는데 브랜치 행을 끝까지 셌다.
     * exists 는 첫 행에서 멈춘다.
     */
    boolean existsByBranch(Branch branch);

    /**
     * 브랜치에 이 버전(추출기)의 특징점이 하나라도 있는가 (UG-357, scaling P0-2).
     *
     * <p>1:N 식별 전 빈 갤러리 판정용이다. 예전 {@code countByBranchAndDescriptorVersion} 은 0 인지만 쓰면서
     * 브랜치 전체를 셌고, 10만 건 1:N 처리 시간의 약 절반이 이 건수 확인이었다(UP-6 측정).
     *
     * <p>버전 조건은 <b>그대로 둔다.</b> 1:N 쿼리({@link DescriptorCustomRepository#oneToManyMatch})는 브랜치로만
     * 거르므로, 건수 확인을 없애고 「1:N 결과가 비었는가」로 바꾸면 다른 버전만 있는 브랜치가
     * EMPTY_GALLERY 대신 엉뚱한 버전과 비교한 결과를 돌려준다.
     */
    boolean existsByBranchAndDescriptorVersion(Branch branch, int version);
}
