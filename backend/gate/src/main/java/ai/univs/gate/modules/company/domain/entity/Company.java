package ai.univs.gate.modules.company.domain.entity;


import ai.univs.gate.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "companies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "company_id")
    private Long id;

    @Column(name = "account_id", nullable = false, unique = true)
    private Long accountId;

    @Column(name = "company_name", length = 100)
    private String companyName;

    @Column(name = "business_number", length = 20)
    private String businessNumber;

    @Column(name = "manager_mail", length = 255)
    private String managerMail;

    @Column(name = "manager_name", length = 100)
    private String managerName;

    @Column(name = "manager_number", length = 100)
    private String managerNumber;

    @Column(name = "main_service", length = 100)
    private String mainService;

    @Column(name = "business_type", length = 100)
    private String businessType;

    @Column(name = "employee_count", length = 100)
    private String employeeCount;

    public void updateInformation(String companyName,
                                  String businessNumber,
                                  String managerMail,
                                  String managerName,
                                  String managerNumber,
                                  String mainService,
                                  String businessType,
                                  String employeeCount
    ) {
        this.companyName = companyName;
        this.businessNumber = businessNumber;
        this.managerMail = managerMail;
        this.managerName = managerName;
        this.managerNumber = managerNumber;
        this.mainService = mainService;
        this.businessType = businessType;
        this.employeeCount = employeeCount;
    }
}
