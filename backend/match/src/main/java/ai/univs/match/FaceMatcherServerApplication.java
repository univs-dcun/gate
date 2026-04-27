package ai.univs.match;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class FaceMatcherServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FaceMatcherServerApplication.class, args);
    }
}
