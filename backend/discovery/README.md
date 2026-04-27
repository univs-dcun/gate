# Discovery Server

![Version](https://img.shields.io/badge/version-1.0.0-blue)

## 개요

---

**Discovery Server**는 Eureka 검색 서비스 or 클라이언트를 의미하는 서버입니다. 
해당 서버를 통해서 서비스들을 관리하고 DNS 의 역할을 가지고 있어  
등록된 서비스 명을 통해 서비스 to 서비스의 요청을 사용할 수 있게됩니다.  

## 사용 범위

---

- 해당 서버를 discovery server 로 등록한 모든 서비스  

## 사용 방법

---

Discovery server 로서 필요한 라이브러리 입니다.  
Spring Cloud 를 사용하기 위한 maven or gradle 이 설정되어있다는 전제하에 라이브러리만 작성했습니다.  

```
implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-server'
```

Discovery server 는 서비스를 관리하는 서버로서 작동됩니다.  
때문에 아래와 같은 설정으로 서비스로서 활동하지 않는다는 설정을 적용합니다.  

```yaml
eureka:
  client:
    register-with-eureka: false
    fetch-registry: false
```

서버는 main 함수를 가지고 있는 클래스에 아래 annotation 을 적용하여 서버로서 작동할 수 있습니다.

```java
@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServerApplication.class, args);
    }
}
```

Discovery server 에 등록이 필요한 서비스로서 필요한 라이브러리 입니다.  

```
implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
```

Discovery server 에 등록이 필요한 서비스는 아래와 같은 설정으로 자신이 등록 대상이라는 설정을 추가합니다.

```yaml
eureka:
  instance:
    instance-id: ${spring.application.name}:${spring.application.instance_id:${random.value}}
    hostname: '여기에 서비스 이름을 적용해 주세요. ex) auth-service'
    # true 설정 시 hostname 프로퍼티는 작동하지 않습니다.
    preferIpAddress: false
  client:
    register-with-eureka: true
    fetch-registry: true
    service-url:
      defaultZone: http://discovery-server:8761/eureka
```

서비스는 main 함수를 가지고 있는 클래스에 아래 annotation 을 적용하여 서비스로서 작동할 수 있습니다.  

```java
@SpringBootApplication
@EnableDiscoveryClient
public class AuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
```


## 주의 사항

---

없음

## 변경 이력 (Changelog)

---

### [v1.1.0] - 2025-12-01
- 스테이징 서버 로그 설정 추가

### [v1.0.0] - 2025-08-07
- ✅ 초기 릴리스

## License

---

Copyright (c) Universe AI Corporation. All rights reserved.