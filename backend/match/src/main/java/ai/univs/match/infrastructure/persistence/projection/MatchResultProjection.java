package ai.univs.match.infrastructure.persistence.projection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchResultProjection {

    private String faceId;
    private Double distance;

    public MatchResultProjection(MatchOracleProjection projection) {
        this.faceId = projection.getFaceId();
        this.distance = (double) projection.getDistance();
    }

    public MatchResultProjection(MatchPostgresqlProjection projection) {
        this.faceId = projection.getFaceId();
        this.distance = projection.getDistance();
    }
}
