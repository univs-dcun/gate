package ai.univs.gate.shared.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Hidden
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/messages")
public class MessageController {

    private final ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<JsonNode> getMessages() throws IOException {
        var resource = new ClassPathResource("messages.json");
        JsonNode json = objectMapper.readTree(resource.getInputStream());
        return ResponseEntity.ok(json);
    }
}
