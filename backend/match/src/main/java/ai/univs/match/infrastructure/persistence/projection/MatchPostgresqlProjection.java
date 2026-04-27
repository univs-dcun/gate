package ai.univs.match.infrastructure.persistence.projection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchPostgresqlProjection {

    private String faceId;
    private Double distance;
}
