package ai.univs.face.application.service;

import ai.univs.face.shared.exception.InvalidFaceImageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static ai.univs.face.shared.web.enums.ErrorType.NO_DOUBLE_SIMILARITY;

@Slf4j
@Service
public class SimilarityParser {

    @Value("${face.match.threshold:0.85}")
    private double FACE_MATCH_THRESHOLD;

    public Double parseDoubleSimilarity(String similarity) {
        try {
            return Double.parseDouble(similarity);
        } catch (NumberFormatException e) {
            // UG-299: 수준 판정은 GlobalExceptionHandler 가 한다. 여기서는 핸들러가
            // 알 수 없는 값만 남긴다 — ERROR 로 두면 한 사건에 ERROR 두 줄이 된다.
            log.warn("similarity 를 실수로 해석하지 못했다 — similarity={}", similarity);

            throw new InvalidFaceImageException(NO_DOUBLE_SIMILARITY);
        }
    }

    public boolean isMatchingBySimilarity(Double similarity) {
        return similarity >= FACE_MATCH_THRESHOLD;
    }

    /**
     * 클라이언트가 지정한 임계치로 판정한다 (UG-314).
     *
     * <p>비교 연산자가 {@link #isMatchingBySimilarity} 와 <b>같아야</b> 한다. 한쪽만
     * {@code >} 로 바뀌면 같은 유사도·같은 임계치에 두 API 가 다른 답을 낸다.
     *
     * <p>스케일은 0.0 ~ 1.0 이다. 클라이언트가 보내는 백분율(0 초과 100 이하)을 이 스케일로
     * 바꾸는 것은 gate 의 몫이다 — 백분율은 클라이언트 대면 표현이고, face 와 match 는 도메인
     * 스케일만 다룬다.
     */
    public boolean isMatchingBySimilarity(Double similarity, double threshold) {
        return similarity >= threshold;
    }

    public double getThreshold() {
        return FACE_MATCH_THRESHOLD;
    }

    public String getThresholdString() {
        return String.valueOf(FACE_MATCH_THRESHOLD);
    }
}
