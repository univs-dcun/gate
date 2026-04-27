package ai.univs.match.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "descriptor")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Descriptor {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "descriptor_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    private String faceId;

    private byte[] descriptor;
    private byte[] descriptorType;
    private byte[] descriptorBody;

    private int descriptorObtainingMethod;
    private int descriptorVersion;
    private int descriptorGeneration;

    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

    public void updateDescriptor(byte[] descriptor,
                                 byte[] descriptorType,
                                 byte[] descriptorBody
    ) {
        this.descriptor = descriptor;
        this.descriptorType = descriptorType;
        this.descriptorBody = descriptorBody;
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        this.modifiedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
