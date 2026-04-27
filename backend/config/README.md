# Config Server

![Version](https://img.shields.io/badge/version-1.0.0-blue)

## 개요

---

**Config Server**는 분산 시스템에서 외부 설정을 위한 서버 또는 클라이언트에 지원을 제공합니다.  

## 사용 범위

---

- 인터넷이 접근 가능한 모든 곳 (서버, 서비스, 클라이언트측, etc)

## 사용 방법

---

Config server 에서 필요한 설정은 아래와 같습니다.  
yaml 의 설정을 설명하자면 Bitbucket 에 설정 파일 전부를 가지고 있는 repository 를 생성하여 Config server 에    
외부 설정 파일 요청이 오는 경우 repository 에 접근하여 파일을 가져오도록 설정하여 사용하고 있습니다.   
또한 필요에 따라 파일 서버를 통해서 파일을 가져올 수 있습니다.  

```
implementation 'org.springframework.cloud:spring-cloud-config-server'
```

```yaml
spring:
  application:
    name: config-server
  cloud:
    config:
      server:
        git:
          uri: https://univs-psh01@bitbucket.org/univs-ai/config-repo.git
          default-label: main
          username: ${GIT_USERNAME}
          password: ${GIT_PASSWORD}
        native:
          search-locations: file:///config-repo
```

```java
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
```

Config server 를 사용하는 서비스는 다음과 같은 설정으로 이용할 수 있습니다.  

```
implementation 'org.springframework.cloud:spring-cloud-starter-config'
```

```yaml
spring:
  application:
    name: auth-service
  config:
    import: optional:configserver:http://config-server:8888
```

## 주의 사항

---

Config server 를 통해서 설정 파일을 불러올 때 사용하는 네이밍 규칙이 있습니다.  
잘 숙지하여 정보를 가져오지 못하는 경우 참조바랍니다.  

Spring Cloud Config 에서 설정 파일 이름은 일반적으로 application-{profile}.yml 또는 application-{profile}.properties 형식을 따릅니다.  
여기서 application은 스프링 부트 애플리케이션의 이름을 나타내고, `{profile}`은 활성화된 프로파일(예: dev, prod, test)을 의미합니다.  
예를 들어, order-service 라는 애플리케이션의 개발 환경 설정을 위한 파일은 order-service-dev.yml 또는 order-service-dev.properties 와 같은 이름을 갖습니다.  

추가적으로 Config server 를 본인의 로컬 서버에서 테스트할 때 OS 가 윈도우 기반인지 리눅스 기반인지에 따라  
사용하는 prefix 값이 다르니 주의해 주시기 바랍니다.  

## 변경 이력 (Changelog)

---

### [v1.1.1] - 2025-12-01
- config 서버에 로그 파일 추가 2

### [v1.1.0] - 2025-12-01
- 스테이징 서버를 위한 테스트용 commit (4번째)

### [v1.0.0] - 2025-08-07
- ✅ 초기 릴리스

## License

---

Copyright (c) Universe AI Corporation. All rights reserved.