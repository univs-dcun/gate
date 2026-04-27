package ai.univs.gate.facade.demo.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DemoRedisPublisher {

    @Value("${redis.demo.channel}")
    public String redisDemoChannel;

    private final StringRedisTemplate stringRedisTemplate;

    public void publish(String message) {
        try {
            stringRedisTemplate.convertAndSend(redisDemoChannel, message);
        } catch (Exception e) {
            log.error("Failed to publish demo result to Redis: {}", e.getMessage(), e);
        }
    }
}
