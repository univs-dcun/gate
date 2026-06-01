# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Palm Service is a Spring Boot application that manages palm vein feature descriptors and provides matching capabilities. It acts as a backend service for palm vein recognition.

## 브랜치 규칙
- **모든 브랜치는 `dev` 브랜치 기반으로 생성한다** (`git checkout dev && git checkout -b {브랜치명}`)
- 브랜치 네이밍: 루트 docs/branch-naming-conventions.md 참고
- PR 대상 브랜치: `dev`

## Build and Test Commands

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Run a specific test class
./gradlew test --tests "ai.univs.palm.SomeTest"

# Run the application locally with PostgreSQL
./gradlew bootRun --args='--spring.profiles.active=local,postgresql'

# Run the application locally with Oracle
./gradlew bootRun --args='--spring.profiles.active=local,oracle'
```

> **주의**: `local` 프로파일 단독 실행 시 DataSource 설정이 없어 오류가 발생합니다.
> 반드시 `local,postgresql` 또는 `local,oracle` 조합으로 실행해야 합니다.

## Architecture

### Layered Structure
- **api/controller**: REST endpoints
- **api/dto**: Request/response DTOs with validation
- **application**: Service interfaces and implementations
- **domain/entity**: JPA entities
- **infrastructure/persistence**: Repositories
- **global**: Cross-cutting concerns (exceptions, enums, swagger, utils)

### Database Strategy
The application supports both PostgreSQL and Oracle databases through Spring profiles:
- `application-postgresql.yml`: PostgreSQL datasource & Flyway config
- `application-oracle.yml`: Oracle datasource & Flyway config
- Flyway migration files: `classpath:db/migration/postgresql` / `classpath:db/migration/oracle`

## Key Conventions

- Response objects are wrapped in `ResponseApi<T>` at the controller level
- All timestamps use UTC (`LocalDateTime.now(ZoneOffset.UTC)`)
- Korean language is used in code comments and Swagger documentation
