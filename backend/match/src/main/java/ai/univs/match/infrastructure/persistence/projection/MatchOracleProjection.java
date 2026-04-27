package ai.univs.match.infrastructure.persistence.projection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchOracleProjection {

    private String faceId;
    private float distance;
}
