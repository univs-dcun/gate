package ai.univs.auth.shared.swagger;

import ai.univs.auth.shared.web.dto.Errors;
import ai.univs.auth.shared.web.dto.ResponseApi;
import ai.univs.auth.shared.web.enums.ErrorType;
import ai.univs.auth.support.message.MessageService;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.groupingBy;

@Configuration
@OpenAPIDefinition(
        info = @io.swagger.v3.oas.annotations.info.Info(
                title = "Univs Auth API",
                version = "1.0.0"
        ),
        servers = {
                @Server(url = "http://localhost:8081")
        }
)
@SecurityScheme(
        type = SecuritySchemeType.HTTP,
        name = "Authentication",
        bearerFormat = "JWT",
        scheme = "bearer"
)
@RequiredArgsConstructor
public class SwaggerConfig {

    private final MessageService messageService;

    @Bean
    public OperationCustomizer customizer() {
        return (Operation operation, HandlerMethod handlerMethod) -> {
            addGlobalHeaders(operation);

            SwaggerErrorExample swaggerErrorExample = handlerMethod.getMethodAnnotation(SwaggerErrorExample.class);
            if (swaggerErrorExample != null) {
                settingExamples(operation, swaggerErrorExample.value());
            }

            return operation;
        };
    }

    private void settingExamples(Operation operation, SwaggerError[] customErrors) {
        Map<String, Integer> allErrors = new HashMap<>();
        allErrors.put(HttpStatus.UNAUTHORIZED.name(), HttpStatus.UNAUTHORIZED.value());
        allErrors.put(HttpStatus.NOT_FOUND.name(), HttpStatus.NOT_FOUND.value());
        allErrors.put(HttpStatus.METHOD_NOT_ALLOWED.name(), HttpStatus.METHOD_NOT_ALLOWED.value());
        allErrors.put(HttpStatus.INTERNAL_SERVER_ERROR.name(), HttpStatus.INTERNAL_SERVER_ERROR.value());

        for (SwaggerError error : customErrors) {
            allErrors.put(error.errorType().name(), error.status());
        }

        ApiResponses responses = operation.getResponses();

        Map<Integer, List<ExampleHolder>> statusWithExampleHolders =
                allErrors.entrySet().stream()
                        .map(entry -> {
                            ErrorType errorType = ErrorType.from(entry.getKey());
                            return ExampleHolder.builder()
                                    .holder(getExample(errorType))
                                    .code(entry.getValue())
                                    .name(entry.getKey())
                                    .build();
                        })
                        .collect(groupingBy(ExampleHolder::getCode));

        addExamplesToResponses(responses, statusWithExampleHolders);
    }

    private Example getExample(ErrorType errorType) {
        String errorMessage = messageService.getMessage(errorType);
        Errors errors = Errors.from(errorType, errorMessage);
        Example example = new Example();
        example.setValue(ResponseApi.fail(errors));
        return example;
    }

    private void addExamplesToResponses(ApiResponses responses,
                                        Map<Integer, List<ExampleHolder>> exampleHolders) {
        exampleHolders.forEach((status, holders) -> {
            MediaType mediaType = new MediaType();
            holders.forEach(h -> mediaType.addExamples(h.getName(), h.getHolder()));

            Content content = new Content();
            content.addMediaType("application/json", mediaType);

            ApiResponse apiResponse = new ApiResponse();
            apiResponse.setContent(content);

            responses.addApiResponse(status.toString(), apiResponse);
        });
    }

    private void addGlobalHeaders(Operation operation) {
        operation.addParametersItem(new Parameter()
                .in("header").name("Accept-Language").required(false)
                .description("응답 언어 설정 (기본값: ko)").example("ko"));

        operation.addParametersItem(new Parameter()
                .in("header").name("Accept-TimeZone").required(false)
                .description("응답 시간대 설정 (기본값: Asia/Seoul)").example("Asia/Seoul"));
    }
}
