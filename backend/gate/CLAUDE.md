# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

UnivsGate is a Spring Boot 3.5 microservice (Java 21) providing e-KYC gateway functionality: account authentication, project/API key management, company management, user management, and face matching (identify/verify/liveness). It runs as a Eureka client behind a Spring Cloud Config Server and uses MariaDB with JPA/QueryDSL. External face AI services are called via Feign clients.

## Build & Run Commands

```bash
# Build
./gradlew build

# Run tests (JUnit 5)
./gradlew test

# Run a single test class
./gradlew test --tests "ai.univs.gate.modules.project.SomeTest"

# Run the application (local profile)
./gradlew bootRun --args='--spring.profiles.active=local'

# Clean (also removes generated QueryDSL Q-classes)
./gradlew clean
```

Local profile connects to MariaDB at `localhost:3307/gate`. Config Server and Eureka discovery are disabled in local profile.

## Architecture

### Layered Module Structure (Clean Architecture style)

Each business module under `ai.univs.gate.modules.<module>` follows this structure:

```
modules/<module>/
  api/
    controller/   — REST controllers, request validation, DTO mapping
    dto/          — Request/Response DTOs (records)
  application/
    usecase/      — Single-responsibility use case classes (@Component, not @Service)
    input/        — Input objects for use cases
    result/       — Output objects from use cases
    service/      — Shared application services (only in auth module)
    exception/    — Module-specific exceptions (only in auth module)
  domain/
    entity/       — JPA entities
    enums/        — Domain enums
    repository/   — Repository interfaces (domain contract)
  infrastructure/
    persistence/  — Repository implementations, JPA repositories, QueryDSL repositories
    client/       — Feign client interfaces and their request/response DTOs (match, user modules)
```

**Key pattern**: Domain repository interfaces live in `domain/repository/`, their JPA/QueryDSL implementations live in `infrastructure/persistence/`. The `*RepositoryImpl` delegates to both a `*JpaRepository` (Spring Data) and optionally a `*DSLRepository` (QueryDSL). Feign clients live in `infrastructure/client/` alongside their DTOs.

### Business Modules

- **auth** — Signup, login/logout, JWT tokens, email verification, password reset/change
- **project** — CRUD for projects with settings (liveness, consent), each project gets auto-generated API keys
- **api_key** — API key retrieval and regeneration
- **company** — Company profile management
- **user** — Full CRUD for users (`/api/v1/users`): create/update/delete/get by userId or faceId, paginated list. Uses `FaceUserClient` (Feign) to sync with external face AI service.
- **match** — e-KYC face matching (`/api/v1/match`): identify (1:N), verify by faceId (1:1), verify by image (1:1), liveness check, match history CRUD. Uses `FaceMatchClient` (Feign). Note: controller class is named `matchController` (lowercase).

### BillingService Integration

Billing logic is handled by a separate **BillingService** microservice (`payment-service` in Eureka). GateService calls it via `PaymentClient` (Feign).

- Local profile: `spring.cloud.openfeign.client.config.payment-service.url=http://localhost:8081`
- Read-only billing entities in `shared/billing/` (`SubscriptionReadModel`, `CreditBalanceReadModel`, `FeatureLimitReadModel`, `PlanType`) are kept in GateService **only** for `ProjectDSLRepository` JOIN queries (same MariaDB instance). These contain no business logic.
- All billing mutations and validations go through `PaymentClient` Feign calls.

### Shared Layer (`shared/`)

- **auth/security** — `SecurityConfig` (stateless JWT), `JwtAuthenticationFilter`, custom entry points
- **auth/UserContext** — ThreadLocal-based user context populated by `UserContextInterceptor` from headers (`X-Account-Id`, `X-Api-Key`, `Accept-TimeZone`)
- **domain** — `BaseEntity`, `BaseTimeEntity` (JPA auditing base classes), `AuditorAwareImpl`, `JpaConfig`
- **exception** — `BusinessException` hierarchy with `ErrorType` enum codes; `GlobalExceptionHandler` handles all exceptions; `CustomFeignException` for Feign error propagation
- **web/dto** — Unified `ResponseApi<T>` wrapper: `{success, data, errors}`; `CustomPage<T>` for paginated responses
- **usecase/result** — `CustomPageResult` shared paginated result record
- **swagger** — `@SwaggerErrorExample` / `@SwaggerError` annotations; `SwaggerDescriptions` constants for common `@Parameter` descriptions
- **utils** — `CustomPageable`, `DateTimeUtil`, `ImageFileValidator` / `@ValidImageFile` (multipart image validation), `TransactionUtil`
- **locale** — `LocaleConfig` for i18n locale resolution

### Support Layer (`support/`)

Cross-cutting services used by multiple modules:

- **api_key** — `ApiKeyGenerator`, `ApiKeyService`
- **file** — `FileService`, `FileUtil`, `CryptoUtil`
- **mail** — `MailService` (Google OAuth2 SMTP via Thymeleaf templates)
- **message** — `MessageService` (i18n)
- **project** — `ProjectService`, `ProjectSettingsService`
- **user** — `UserService`
- **match** — `MatchService`
- **face** — `FaceService`
- **qr** — `QrCodeService`
- **feign** — `CommonFeignConfig`, `CommonErrorDecoder`, `ClientResponseApi` (shared Feign infrastructure)
- **billing/client** — `PaymentClient` (Feign interface to BillingService). All billing operations (validate/deduct usage, project init/deletion, free-plan limit check) are delegated to BillingService via this client. DTOs: `BillingOperationFeignRequestDTO` `{projectId, accountId}`, `BillingDeductFeignRequestDTO` `{projectId, accountId}`, `ProjectInitFeignRequestDTO` `{accountId}`, `BillingSummaryFeignResponseDTO`.

### Facade Layer (`facade/`)

BFF/demo layer for cross-domain orchestration. Currently contains `facade/demo/`:

- QR code generation for demo flows (create user, verify, identify, liveness)
- Token-based operations: QR codes embed a short-lived UUID code (not JWT); client exchanges `code` → JWT via `DemoQrCodeService.exchangeCode()`
- Entity: `DemoQrCode` (code, jwt, type, expiresAt, isUsed)
- Endpoints: `/api/v1/demo/**` (all permitAll in SecurityConfig)

## Conventions

- **Use cases, not services**: Business logic goes in `*UseCase` classes annotated with `@Component`. Each use case has a single `execute()` method.
- **DTOs are records**: Request/Response DTOs and Result objects use Java records.
- **Error handling**: Throw `CustomGateException(ErrorType.XXX)` or module-specific exceptions extending `BusinessException`. Error messages are resolved via i18n (`messages_en.properties`, `messages_ko.properties`) keyed by `ErrorType` enum name.
- **i18n**: Error messages support Korean and English via `messages_ko.properties` / `messages_en.properties`. Locale is resolved from the request.
- **Soft delete**: Entities use `isDeleted` flag rather than physical deletion.
- **Swagger docs**: Controllers use `@SwaggerErrorExample` with `@SwaggerError` annotations to declare possible error responses per endpoint. Use `SwaggerDescriptions` constants for `@Parameter` descriptions.
- **QueryDSL**: Q-classes are generated to `build/generated/querydsl/`. Run `./gradlew clean` if Q-classes get stale.
- **API path convention**: All endpoints are under `/api/v1/`.
- **Multipart uploads**: Image fields use `@ValidImageFile` (custom constraint backed by `ImageFileValidator`).
- **Pagination**: Use `CustomPageable` for query params → `CustomPageResult` / `CustomPage` for responses.
