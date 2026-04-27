package ai.univs.gate.shared.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Tag(name = "메시지")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/messages")
public class MessageController {

    private final ObjectMapper objectMapper;

    @Operation(
            summary = "e-KYC 메시지",
            description = "등록, 1:1 확인, 1:1 매칭, 라이브니스에서 발생하는 타입, 메시지를 json 타입의 문자열로 제공합니다."
    )
    @GetMapping
    public ResponseEntity<JsonNode> getMessages() throws IOException {
        var resource = new ClassPathResource("messages.json");
        JsonNode json = objectMapper.readTree(resource.getInputStream());
        return ResponseEntity.ok(json);
    }
}
