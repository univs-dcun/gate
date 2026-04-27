package ai.univs.match.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "branch")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "branch_id")
    private Long id;

    private String branchName;

    @Builder.Default
    @OneToMany(mappedBy = "branch")
    private List<Descriptor> descriptorEntities = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
}
