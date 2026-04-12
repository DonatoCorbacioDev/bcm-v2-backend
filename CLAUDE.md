# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Development Commands

```bash
# Build
mvn clean install

# Run (dev profile is default)
mvn spring-boot:run
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=ContractServiceTest

# Run a single test method
mvn test -Dtest=ContractServiceTest#shouldReturnContractWhenValidIdProvided

# Run tests with coverage report (output: target/site/jacoco/index.html)
mvn clean test jacoco:report

# Static analysis
mvn spotbugs:check

# Production build
mvn clean package -Pprod
java -jar target/bcm-backend-1.0.0-SNAPSHOT.jar --spring.profiles.active=prod
```

## Environment Setup

Copy `.env.example` to `.env` and configure:
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` — MySQL 8.0+ connection
- `JWT_SECRET` — Base64-encoded, minimum 256 bits
- `FRONTEND_BASE_URL` — CORS origin
- `MAIL_*` — SMTP settings

Create the database before first run:
```sql
CREATE DATABASE bcm CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Flyway auto-applies all migrations on startup. Five migration files live in `src/main/resources/db/migration/` (V1–V5).

## Maven Profiles

| Profile | Database | Flyway | Use |
|---------|----------|--------|-----|
| `dev` (default) | MySQL | baseline-on-migrate | Local development |
| `test` | H2 in-memory | disabled | Test execution |
| `prod` | MySQL | validate-on-migrate | Production |

## Architecture

The app is a stateless REST API running on port 8090 with context path `/api/v1`.

**Package layout** under `com.donatodev.bcm_backend`:

```
auth/         AuthController + AuthService (login, invite flow, password reset)
config/       CorsConfig, OpenApiConfig
controller/   8 REST controllers
dto/          19 record-based DTOs (immutable, used as API contracts)
entity/       14 JPA entities (Lombok @Builder, enum ContractStatus)
exception/    Per-entity *NotFoundException classes + GlobalExceptionHandler
jwt/          JwtUtils, JwtAuthenticationFilter, JwtAuthEntryPoint
mapper/       8 MapStruct mappers (toDTO / toEntity; some inject repositories for lookups)
repository/   11 Spring Data JPA repositories (custom @Query for complex searches)
security/     SecurityConfig (Spring Security 6, stateless JWT chain)
service/      16 service classes (@Transactional business logic)
util/         JwtKeyGenerator, TestDataCleaner
```

**Request flow:**
`HTTP → JwtAuthenticationFilter → SecurityConfig rules → @PreAuthorize → Controller (@Valid) → Service (@Transactional) → Mapper → Repository → DB`

**Key design decisions:**
- All DTOs are Java records — do not convert to classes unless necessary.
- MapStruct mappers are Spring components (`componentModel = "spring"`). Some mappers inject repositories to resolve related entities during mapping.
- Role-based data visibility is enforced at the service layer: admins see all records, managers see only their assigned contracts.
- `IEmailService` interface with two implementations: `EmailService` (SMTP) for prod, `DummyEmailService` for dev/test. The active profile selects the bean.
- Contract expiration is handled by a `@Scheduled` task — `@EnableScheduling` is on the main application class.
- `daysUntilExpiry` is a calculated field computed in `ContractMapper`, not stored in the DB.

## Testing Conventions

- Tests use `@ActiveProfiles("test")` → H2 in-memory DB, Flyway disabled.
- `@SpringBootTest` + `MockMvc` for integration/controller tests.
- `@MockitoBean` to mock dependencies in Spring context tests.
- `@WithMockUser` to inject a security principal.
- Nested `@Nested` classes group related test cases within a test class.
- `@ParameterizedTest` with `@CsvSource` for data-driven cases.
- JaCoCo enforces a 75% minimum coverage threshold on every build; DTOs, entities, config, and the main app class are excluded from measurement.

## API Surface

- Swagger UI: `http://localhost:8090/api/v1/swagger-ui.html`
- API docs JSON: `http://localhost:8090/api/v1/api-docs`
- Health: `http://localhost:8090/api/v1/actuator/health`

## Code Quality

SpotBugs + FindSecBugs run as part of the build. The project is also configured for SonarQube analysis. Keep all new code free of SpotBugs violations before committing.
