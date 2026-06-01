package ai.univs.gate.facade.sdk.domain.entity;

import ai.univs.gate.facade.sdk.domain.enums.SdkQrCodeType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "sdk_qr_codes")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SdkQrCode {

    @Id
    private String code;

    @Column(nullable = false)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SdkQrCodeType type;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private Boolean isUsed;

    public boolean isExpired() {
        return LocalDateTime.now(ZoneOffset.UTC).isAfter(expiresAt);
    }

    public void markAsUsed() {
        this.isUsed = true;
    }
}
