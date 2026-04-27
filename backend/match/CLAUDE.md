# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Face Matcher Server is a Spring Boot application that manages facial feature descriptors and provides 1:1 and 1:N face matching capabilities. It acts as a backend service for face recognition, accessed through a face-service intermediary.

## Build and Test Commands

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Run a specific test class
./gradlew test --tests "ai.univs.platform.matcher.application.MatcherServiceImplTest"

# Run a specific test method
./gradlew test --tests "ai.univs.platform.matcher.application.MatcherServiceImplTest.Add.whenHasAllOfTheParamsAndNoBranch_thenSucceed"

# Run the application locally with PostgreSQL
./gradlew bootRun --args='--spring.profiles.active=local,postgresql'

# Run the application locally with Oracle
./gradlew bootRun --args='--spring.profiles.active=local,oracle'
```

## Architecture

### Layered Structure
- **api/controller**: REST endpoints (`/api/v1/match`)
- **api/dto**: Request/response DTOs with validation
- **application**: Service interfaces and implementations
- **domain/entity**: JPA entities (Branch, Descriptor)
- **infrastructure/persistence**: Repositories with database-specific implementations
- **global**: Cross-cutting concerns (exceptions, enums, swagger, utils)

### Database Strategy
The application supports both PostgreSQL and Oracle databases through Spring profiles:
- `RepositoryConfig` selects the appropriate `DescriptorCustomRepository` implementation based on active profile
- Custom matching queries use database-specific native SQL with `vlmatch()` function for face similarity calculation
- Testcontainers are used for integration tests with both databases

### Core Domain Concepts
- **Branch**: A namespace/grouping for descriptors (e.g., per tenant or use case)
- **Descriptor**: Face feature data with versioning. Structure:
  - First 8 bytes: `descriptorType` (metadata including version at byte[4])
  - Remaining 512 bytes: `descriptorBody` (actual face feature vector)
- **DescriptorSpec**: Enum defining supported descriptor versions (59, 60, 62) with Platt scaling parameters for similarity conversion

### Matching Operations
- **1:1 matching (verify)**: Compare a descriptor against a specific faceId or another descriptor
- **1:N matching (identify)**: Find the closest match in a branch using `vlmatch()` database function
- Similarity threshold of 0.85 is used for duplicate detection during registration/update

## Key Conventions

- Response objects are wrapped in `ResponseApi<T>` at the controller level
- All timestamps use UTC (`LocalDateTime.now(ZoneOffset.UTC)`)
- Descriptors are transmitted as Base64-encoded strings
- Korean language is used in code comments and Swagger documentation
