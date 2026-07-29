package ai.univs.gate.modules.company.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ai.univs.gate.modules.company.application.result.CompanyResult;
import ai.univs.gate.modules.company.domain.entity.Company;
import ai.univs.gate.modules.company.domain.repository.CompanyRepository;
import ai.univs.gate.shared.exception.CustomGateException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetCompanyUseCase 단위 테스트")
class GetCompanyUseCaseTest {

    private static final Long ACCOUNT_ID = 10L;
    private static final String EMAIL = "manager@univs.ai";

    @Mock private CompanyRepository companyRepository;

    @InjectMocks private GetCompanyUseCase getCompanyUseCase;

    @Test
    @DisplayName("회사 정보가 존재하면 그대로 반환하고 새로 생성하지 않는다")
    void execute_existingCompany_returnsIt() {
        // given
        Company existing = Company.builder()
                .id(1L)
                .accountId(ACCOUNT_ID)
                .companyName("univs")
                .managerMail("origin@univs.ai")
                .build();
        given(companyRepository.findByAccountId(ACCOUNT_ID)).willReturn(Optional.of(existing));

        // when
        CompanyResult result = getCompanyUseCase.execute(ACCOUNT_ID, EMAIL);

        // then
        assertThat(result.companyId()).isEqualTo(1L);
        assertThat(result.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(result.companyName()).isEqualTo("univs");
        assertThat(result.managerMail()).isEqualTo("origin@univs.ai");
        verify(companyRepository, never()).save(any(Company.class));
    }

    @Test
    @DisplayName("회사 정보가 없으면 managerMail만 채워진 빈 문자열 회사 정보를 lazy 생성하여 반환한다")
    void execute_missingCompany_lazilyCreates() {
        // given
        given(companyRepository.findByAccountId(ACCOUNT_ID)).willReturn(Optional.empty());
        given(companyRepository.save(any(Company.class))).willAnswer(invocation -> {
            Company saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        // when
        CompanyResult result = getCompanyUseCase.execute(ACCOUNT_ID, EMAIL);

        // then: 저장된 엔티티 필드 검증 — 기존 internal/init과 동일하게 null이 아닌 빈 문자열
        ArgumentCaptor<Company> captor = ArgumentCaptor.forClass(Company.class);
        verify(companyRepository).save(captor.capture());
        Company saved = captor.getValue();
        assertThat(saved.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(saved.getManagerMail()).isEqualTo(EMAIL);
        assertThat(saved.getCompanyName()).isEmpty();
        assertThat(saved.getBusinessNumber()).isEmpty();
        assertThat(saved.getManagerName()).isEmpty();
        assertThat(saved.getManagerNumber()).isEmpty();
        assertThat(saved.getMainService()).isEmpty();
        assertThat(saved.getBusinessType()).isEmpty();
        assertThat(saved.getEmployeeCount()).isEmpty();

        // then: 결과 필드 검증
        assertThat(result.companyId()).isEqualTo(2L);
        assertThat(result.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(result.managerMail()).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("이메일 헤더가 없어도(null) managerMail을 빈 문자열로 채워 생성한다")
    void execute_missingCompanyWithoutEmail_createsWithEmptyMail() {
        // given
        given(companyRepository.findByAccountId(ACCOUNT_ID)).willReturn(Optional.empty());
        given(companyRepository.save(any(Company.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        CompanyResult result = getCompanyUseCase.execute(ACCOUNT_ID, null);

        // then
        assertThat(result.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(result.managerMail()).isEmpty();
    }

    @Test
    @DisplayName("동시 첫 조회 경합으로 unique 제약에 걸리면 먼저 생성된 행을 재조회하여 반환한다")
    void execute_concurrentFirstAccess_recoversByReread() {
        // given: 첫 조회는 비어 있고, save는 경합으로 실패하며, 재조회는 상대가 만든 행을 반환
        Company winner = Company.builder()
                .id(3L)
                .accountId(ACCOUNT_ID)
                .managerMail(EMAIL)
                .build();
        given(companyRepository.findByAccountId(ACCOUNT_ID))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(winner));
        given(companyRepository.save(any(Company.class)))
                .willThrow(new DataIntegrityViolationException("uq_companies_account_id"));

        // when
        CompanyResult result = getCompanyUseCase.execute(ACCOUNT_ID, EMAIL);

        // then
        assertThat(result.companyId()).isEqualTo(3L);
        assertThat(result.accountId()).isEqualTo(ACCOUNT_ID);
    }

    @Test
    @DisplayName("accountId가 null이면 CustomGateException이 발생한다")
    void execute_nullAccountId_throwsException() {
        assertThatThrownBy(() -> getCompanyUseCase.execute(null, EMAIL))
                .isInstanceOf(CustomGateException.class);

        verifyNoInteractions(companyRepository);
    }
}
