package ai.univs.gate.modules.company.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;

import ai.univs.gate.modules.company.application.result.CompanyResult;
import ai.univs.gate.modules.company.domain.entity.Company;
import ai.univs.gate.modules.company.domain.repository.CompanyRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    @DisplayName("회사 정보가 없으면 managerMail이 채워진 빈 회사 정보를 lazy 생성하여 반환한다")
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

        // then: 저장된 엔티티 필드 검증
        ArgumentCaptor<Company> captor = ArgumentCaptor.forClass(Company.class);
        verify(companyRepository).save(captor.capture());
        Company saved = captor.getValue();
        assertThat(saved.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(saved.getManagerMail()).isEqualTo(EMAIL);
        assertThat(saved.getCompanyName()).isNull();

        // then: 결과 필드 검증
        assertThat(result.companyId()).isEqualTo(2L);
        assertThat(result.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(result.managerMail()).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("이메일 헤더가 없어도(null) 회사 정보를 생성한다")
    void execute_missingCompanyWithoutEmail_createsWithNullMail() {
        // given
        given(companyRepository.findByAccountId(ACCOUNT_ID)).willReturn(Optional.empty());
        given(companyRepository.save(any(Company.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        CompanyResult result = getCompanyUseCase.execute(ACCOUNT_ID, null);

        // then
        assertThat(result.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(result.managerMail()).isNull();
    }
}
