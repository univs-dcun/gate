package ai.univs.palm.shared.feign;

import ai.univs.palm.shared.exception.CustomFeignException;
import ai.univs.palm.shared.exception.CustomPalmException;
import ai.univs.palm.shared.feign.dto.ProblemDetailFeignResponseDTO;
import ai.univs.palm.shared.web.enums.ErrorType;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CommonErrorDecoder implements ErrorDecoder {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Exception decode(String s, Response response) {
        int status = response.status();

        if (status >= 400 && status < 500) {
            ProblemDetailFeignResponseDTO problem = parseProblemDetail(response);
            String code = "PALM-" + status;
            String type = problem.getTitle() != null
                    ? problem.getTitle().toUpperCase().replace(" ", "_")
                    : "PALM_MODULE_ERROR";
            String message = problem.getDetail() != null
                    ? problem.getDetail()
                    : "Palm module error occurred";
            return new CustomFeignException(code, type, message);
        }

        // 3xx or 5xx
        return new CustomPalmException(ErrorType.INTERNAL_SERVER_ERROR);
    }

    private ProblemDetailFeignResponseDTO parseProblemDetail(Response response) {
        try {
            return mapper.readValue(response.body().asInputStream(), ProblemDetailFeignResponseDTO.class);
        } catch (Exception e) {
            log.error("Parse error for ProblemDetail response: {}", e.getMessage(), e);
            throw new CustomPalmException(ErrorType.INTERNAL_SERVER_ERROR);
        }
    }
}
