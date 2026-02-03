# 🏢 BCM v2.0 - Business Contracts Manager

> Enterprise-grade SaaS platform for contract lifecycle management built with Spring Boot 3.5.9 and Java 21

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.9-brightgreen?logo=spring)](https://spring.io/projects/spring-boot)
[![Coverage](https://img.shields.io/badge/Coverage-100%25%20Perfect-brightgreen?style=flat&logo=codecov)](./target/site/jacoco/index.html)
[![Tests](https://img.shields.io/badge/Tests-316%20methods-success)](./target/site/jacoco/index.html)
[![License](https://img.shields.io/badge/License-Custom-blue)](./LICENSE)
[![Database](https://img.shields.io/badge/Database-MySQL%208.0-blue?logo=mysql)](https://www.mysql.com/)
[![Flyway](https://img.shields.io/badge/Migrations-Flyway-red?logo=flyway)](https://flywaydb.org/)

## 🎯 Overview

BCM v2.0 is the second iteration of my Business Contract Manager system, representing a complete architectural redesign from the original version developed during my master's thesis. This version showcases modern Spring Boot best practices, **100% test coverage** (instruction and branch), production-ready security features, and automated database versioning with Flyway.

**Project Type:** Portfolio Project | Full-Stack SaaS Backend  
**Status:** Active Development  
**Author:** Donato Corbacio  
**Contact:** donatocorbacio92@gmail.com

---

## ✨ Key Features

### Contract Management

- Full CRUD operations for contract lifecycle
- Advanced search and filtering with pagination
- Multi-manager assignment per contract
- Contract status tracking (ACTIVE, EXPIRED, CANCELLED, etc.)
- Audit trail with complete history

### Security & Authentication

- JWT-based stateless authentication
- BCrypt password hashing
- Role-based access control (ADMIN, MANAGER)
- Email verification system
- Password reset functionality
- Invite-only user registration

### Business Logic

- Role-specific contract visibility (Admins see all, Managers see assigned)
- Collaborative contract management
- Financial values tracking per contract
- Business area organization
- Real-time dashboard KPIs

### Database Management

- Automated schema versioning with Flyway
- Zero-downtime migrations
- Rollback capabilities
- Multi-environment consistency (dev/test/prod)

---

## 🏗️ Architecture

### Clean Architecture Pattern

```
┌─────────────────────────────────────────┐
│       Controllers (REST API)            │  HTTP Layer
├─────────────────────────────────────────┤
│          Services                       │  Business Logic
├─────────────────────────────────────────┤
│    Mappers (DTO ↔ Entity)              │  Data Transformation
├─────────────────────────────────────────┤
│    Repositories (Spring Data)           │  Data Access
├─────────────────────────────────────────┤
│         Entities (JPA)                  │  Domain Models
└─────────────────────────────────────────┘
```

### Technology Stack

**Backend Framework:**

- Spring Boot 3.5.9 (Latest stable)
- Java 21 LTS
- Maven for dependency management

**Database:**

- MySQL 8.0
- Spring Data JPA / Hibernate
- HikariCP connection pooling
- Flyway for database migrations and version control

**Security:**

- Spring Security 6
- JWT (JJWT 0.12.6)
- BCrypt password encoder
- OAuth2 Client (prepared for future integrations)

**Testing:**

- JUnit 5 (Jupiter)
- Mockito for mocking
- Spring Boot Test
- Testcontainers (for integration tests)
- H2 in-memory database (test environment)

**Code Quality & Analysis:**

- JaCoCo (100% test coverage)
- SpotBugs for bug detection
- FindSecBugs for security analysis
- SonarQube compatible

**API Documentation:**

- SpringDoc OpenAPI 3.0
- Swagger UI integration

**Additional Libraries:**

- MapStruct for DTO mapping
- Lombok for boilerplate reduction
- Hibernate Validator

---

## 📊 Code Quality Metrics

| Metric                    | Value                        | Status              |
| ------------------------- | ---------------------------- | ------------------- |
| **Test Coverage**         | 100% (0 missed instructions) | ✅ Perfect          |
| **Branch Coverage**       | 100% (0 missed branches)     | ✅ Perfect          |
| **Test Classes**          | 54 classes                   | ✅ Comprehensive    |
| **Test Methods**          | 316 methods                  | ✅ Extensive        |
| **Lines Covered**         | 1,092 lines                  | ✅ Full coverage    |
| **Cyclomatic Complexity** | 417 (0 missed)               | ✅ Well tested      |
| **Security Scan**         | No issues                    | ✅ FindSecBugs pass |
| **Architecture**          | Clean separation             | ✅ Professional     |
| **Package Coverage**      | All packages: 100%           | ✅ Perfect          |

---

## 🗂️ Project Structure

```
src/main/java/com/donatodev/bcm_backend/
├── auth/              # Authentication controllers and services
├── config/            # Application configuration (CORS, etc.)
├── controller/        # REST API controllers
├── dto/               # Data Transfer Objects
├── entity/            # JPA entities (domain models)
├── exception/         # Custom exceptions + GlobalExceptionHandler
├── jwt/               # JWT utilities and filters
├── mapper/            # DTO ↔ Entity mappers (MapStruct)
├── repository/        # Spring Data JPA repositories
├── security/          # Security configuration
├── service/           # Business logic layer
└── util/              # Utility classes

src/test/java/         # Mirror structure with comprehensive tests
src/main/resources/
├── application.properties           # Main configuration
├── application-dev.properties       # Development profile
├── application-prod.properties      # Production profile
└── db/migration/                    # Flyway migration scripts
    ├── V1__initial_schema.sql       # Database schema
    ├── V2__seed_reference_data.sql  # Reference data
    └── V3__add_performance_indexes.sql  # Performance optimization

sql/                   # Legacy SQL files (reference only, deprecated)
```

---

## 🗄️ Database Migrations (Flyway)

This project uses **Flyway** for automatic database version control and migrations, ensuring consistent schemas across all environments.

### Migration Files

Located in `src/main/resources/db/migration/`:

| File                                  | Version | Description                                           |
| ------------------------------------- | ------- | ----------------------------------------------------- |
| **V1\_\_initial_schema.sql**          | v1      | Creates all database tables (10 tables)               |
| **V2\_\_seed_reference_data.sql**     | v2      | Inserts system roles, business areas, financial types |
| **V3\_\_add_performance_indexes.sql** | v3      | Adds 9 performance indexes for optimized queries      |

### How It Works

1. **First Startup:** Flyway detects an empty database and executes all migrations sequentially
2. **Version Tracking:** Creates `flyway_schema_history` table to track applied migrations
3. **Subsequent Startups:** Only runs new migrations (version > current)
4. **Automatic Execution:** Migrations run automatically on `mvn spring-boot:run`

### Benefits

- ✅ **Zero manual SQL execution** - Fully automated
- ✅ **Environment consistency** - Same schema in dev/test/prod
- ✅ **Team collaboration** - Everyone shares the same DB version
- ✅ **Rollback support** - Track and revert changes safely
- ✅ **Production-ready** - Enterprise-grade versioning

### Flyway Commands

```bash
# View migration status
mvn flyway:info

# Manually trigger migrations (rarely needed)
mvn flyway:migrate

# Validate checksums
mvn flyway:validate

# Repair migration history (if needed)
mvn flyway:repair

# Clean database (⚠️ DANGER: deletes all data!)
mvn flyway:clean
```

### Adding New Migrations

When you need to modify the database:

1. Create new file with incremented version: `V4__your_description.sql`
2. Write your SQL DDL/DML changes
3. Restart application → Flyway detects and applies automatically
4. Commit migration file to Git

**Example:**

```sql
-- V4__add_contract_documents_table.sql
CREATE TABLE contract_documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_url VARCHAR(500) NOT NULL,
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (contract_id) REFERENCES contracts(id) ON DELETE CASCADE
);
```

---

## 🛠️ Setup Instructions

### Prerequisites

- **Java 21** or higher
- **MySQL 8.0+** (or compatible)
- **Maven 3.8+**
- **Git**

### 1. Clone Repository

```bash
git clone https://github.com/DonatoCorbacioDev/bcm-v2-backend.git
cd bcm-v2-backend
```

### 2. Database Setup

**✅ With Flyway (Recommended - Fully Automated):**

```bash
# Login to MySQL
mysql -u root -p

# Create empty database
CREATE DATABASE bcm CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# Exit MySQL
exit

# Flyway will automatically create tables and seed data on first startup!
```

**That's it!** On application startup, Flyway will:

- ✅ Create all 10 database tables
- ✅ Insert system roles (ADMIN, MANAGER)
- ✅ Add business areas and financial types
- ✅ Create performance indexes

**📜 Manual Setup (Legacy - Not Recommended):**

Only use if you want to bypass Flyway:

```bash
# Create database and run schema manually
mysql -u root -p bcm < sql/DDL/bcm_schema.sql

# (Optional) Load additional sample data
mysql -u root -p bcm < sql/DML/bmc_data.sql
```

⚠️ **Note:** The `sql/` folder contains legacy scripts for reference only. In production, **always use Flyway**.

### 3. Environment Configuration

Create a `.env` file in the project root (see `.env.example` for template):

```bash
# Database
DB_URL=jdbc:mysql://localhost:3306/bcm
DB_USERNAME=your_username
DB_PASSWORD=your_password

# JWT
JWT_SECRET=your-base64-encoded-secret-key-minimum-256-bits
JWT_EXPIRATION_MS=86400000

# Email (Gmail example)
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-gmail-app-password
MAIL_SMTP_AUTH=true
MAIL_SMTP_STARTTLS=true

# Frontend URL (for CORS)
FRONTEND_BASE_URL=http://localhost:3000
```

**Note:** For Gmail, use an [App Password](https://support.google.com/accounts/answer/185833), not your regular password.

### 4. Build and Run

```bash
# Install dependencies and build
mvn clean install

# Run in development mode (Flyway will auto-migrate)
mvn spring-boot:run

# Or specify profile explicitly
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

**Expected Output:**

```
[INFO] Flyway: Migrating schema `bcm` to version "1 - initial schema"
[INFO] Flyway: Migrating schema `bcm` to version "2 - seed reference data"
[INFO] Flyway: Migrating schema `bcm` to version "3 - add performance indexes"
[INFO] Flyway: Successfully applied 3 migrations to schema `bcm`, now at version v3
[INFO] Started BcmBackendApplication in 7.873 seconds
```

The application will start at: `http://localhost:8090/api/v1`

### 5. Access API Documentation

Once running, visit:

- **Swagger UI:** http://localhost:8090/api/v1/swagger-ui.html
- **OpenAPI JSON:** http://localhost:8090/api/v1/api-docs
- **Health Check:** http://localhost:8090/api/v1/actuator/health

---

## 🧪 Testing

### Run Tests

```bash
# Run all tests
mvn test

# Run tests with coverage report
mvn clean test jacoco:report

# View coverage report
open target/site/jacoco/index.html

# Run specific test class
mvn test -Dtest=ContractServiceTest

# Run tests for specific package
mvn test -Dtest="com.donatodev.bcm_backend.service.*Test"

# Skip tests during build
mvn clean package -DskipTests
```

### Test Coverage by Package

| Package    | Coverage | Key Tests                        |
| ---------- | -------- | -------------------------------- |
| service    | 100%     | Business logic (all services)    |
| controller | 100%     | REST endpoints (all controllers) |
| mapper     | 100%     | All DTO mappings                 |
| auth       | 100%     | AuthService, AuthController      |
| jwt        | 100%     | Token generation/validation      |
| security   | 100%     | SecurityConfig                   |
| exception  | 100%     | Global exception handling        |
| util       | 100%     | Utility classes                  |

**Perfect Score:** All packages achieve 100% instruction and branch coverage.

**Note:** Tests use H2 in-memory database with Flyway disabled for speed.

---

## 📚 API Endpoints

### Authentication

```
POST   /api/v1/auth/login              # User login
POST   /api/v1/auth/forgot-password    # Request password reset
POST   /api/v1/auth/reset-password     # Reset password with token
```

### Contracts

```
GET    /api/v1/contracts               # List all contracts (ADMIN)
GET    /api/v1/contracts/{id}          # Get contract by ID
GET    /api/v1/contracts/search        # Search with pagination & filters
POST   /api/v1/contracts               # Create new contract (ADMIN)
PUT    /api/v1/contracts/{id}          # Update contract (ADMIN)
DELETE /api/v1/contracts/{id}          # Delete contract (ADMIN)
GET    /api/v1/contracts/stats         # Dashboard statistics
PATCH  /api/v1/contracts/{id}/assign-manager  # Assign manager
GET    /api/v1/contracts/{id}/collaborators   # Get collaborators
PATCH  /api/v1/contracts/{id}/collaborators   # Set collaborators
```

### Users

```
GET    /api/v1/users                   # List users
GET    /api/v1/users/{id}              # Get user by ID
POST   /api/v1/users/invite            # Invite new user (ADMIN)
POST   /api/v1/users/complete-invite   # Complete registration
PATCH  /api/v1/users/{id}/assign-manager  # Assign manager to user
```

### Managers

```
GET    /api/v1/managers                # List all managers
GET    /api/v1/managers/{id}           # Get manager by ID
POST   /api/v1/managers                # Create manager
PUT    /api/v1/managers/{id}           # Update manager
DELETE /api/v1/managers/{id}           # Delete manager
```

### Other Endpoints

```
GET    /api/v1/roles                   # List roles
GET    /api/v1/business-areas          # List business areas
GET    /api/v1/financial-types         # List financial types
GET    /api/v1/financial-values        # Financial values CRUD
```

**Authentication:** Most endpoints require JWT token in `Authorization: Bearer <token>` header.

---

## 🔒 Security Features

### Implemented Security Measures

- ✅ Stateless JWT authentication
- ✅ BCrypt password hashing (strength: 12 rounds)
- ✅ CORS configuration (programmatic)
- ✅ SQL injection protection (JPA parameterized queries)
- ✅ XSS protection headers
- ✅ CSRF disabled (API-only, stateless)
- ✅ Role-based authorization with `@PreAuthorize`
- ✅ Email verification required before login
- ✅ Password reset with expiring tokens
- ✅ Sensitive endpoints restricted by role
- ✅ No passwords in logs or responses
- ✅ Environment variables for secrets (`.env` git-ignored)

### Security Scanning

```bash
# Run SpotBugs security analysis
mvn spotbugs:check

# View security report
open target/spotbugs.xml
```

---

## 🚀 Deployment

### Environment Profiles

The application supports multiple profiles:

- **dev** (default): Development mode with debug logging, Flyway baseline-on-migrate
- **test**: Testing environment with H2 database, Flyway disabled
- **prod**: Production mode with optimized settings, Flyway validate-on-migrate

### Production Deployment Checklist

Before deploying to production:

- [ ] Generate strong JWT secret (minimum 256 bits, Base64-encoded)
- [ ] Use production database with SSL/TLS
- [ ] Configure `application-prod.properties` with production values
- [ ] Verify Flyway migrations are tested
- [ ] Enable HTTPS only
- [ ] Set up automated database backups
- [ ] Configure monitoring (Actuator + external service)
- [ ] Review and restrict CORS origins
- [ ] Set up log aggregation
- [ ] Enable rate limiting (external solution)
- [ ] Perform security audit
- [ ] Set up CI/CD pipeline

### Example Production Run

```bash
# Build production artifact
mvn clean package -Pprod

# Run with production profile
java -jar target/bcm-backend-1.0.0-SNAPSHOT.jar --spring.profiles.active=prod

# Or with Docker (future)
docker build -t bcm-backend:1.0.0 .
docker run -p 8090:8090 --env-file .env.prod bcm-backend:1.0.0
```

---

## 📝 Version History

### Version 2.0 (Current - 2025)

**Major Rewrite:**

- ✨ Migrated to Spring Boot 3.5.9 + Java 21
- ✨ Redesigned architecture with clean layers
- ✨ JWT-based authentication
- ✨ Comprehensive test suite (100% coverage - perfect score)
- ✨ Role-based access control
- ✨ Email verification system
- ✨ Multi-manager support per contract
- ✨ Advanced search and pagination
- ✨ OpenAPI 3 documentation
- ✨ Production-ready security
- ✨ **Flyway database migrations (automatic versioning)**
- ✨ **Multi-environment configuration (dev/test/prod)**
- ✨ **Enterprise-grade database management**

### Version 1.0 (2024)

**Original Implementation:**

- Initial version developed during master's thesis
- Angular + Spring Boot + MySQL stack
- Basic CRUD functionality
- Single-manager assignment
- Simple authentication
- Manual SQL script execution

---

## 🐛 Known Limitations & Future Improvements

### Current Limitations

- Email service uses simple SMTP (consider AWS SES for production)
- No multi-tenancy support (single organization)
- No file upload/storage for contract documents
- No real-time notifications (WebSocket)
- No audit logs for all actions

### Planned Improvements

- [ ] Add document storage (AWS S3 integration)
- [ ] Implement multi-tenancy for SaaS model
- [ ] Add WebSocket notifications
- [ ] Create comprehensive audit logging
- [ ] Add export functionality (PDF, Excel)
- [ ] Implement contract templates
- [ ] Add AI-powered contract analysis (Python microservice)
- [ ] Performance optimization for large datasets
- [ ] Add caching layer (Redis)
- [ ] Docker containerization
- [ ] Kubernetes deployment manifests

---

## 👨‍💻 About the Developer

**Donato Corbacio**

- 🎓 Bachelor's Degree in Computer Science and Software Production Technologies (Dec 2024)
- 💼 Junior Full-Stack Developer seeking opportunities
- 📚 Currently studying: Python IFTS & AI Automation Business
- 🌍 Based in Puglia, Italy
- 💡 Passionate about clean code, modern architecture, and continuous learning

### Contact & Links

- 📧 Email: donatocorbacio92@gmail.com
- 💼 LinkedIn: [linkedin.com/in/donato-corbacio](https://www.linkedin.com/in/donato-corbacio/)
- 🐱 GitHub: [@DonatoCorbacioDev](https://github.com/DonatoCorbacioDev)
- 🌐 Portfolio: [Coming Soon]

---

## 📄 License

This project is licensed under a **Custom Non-Commercial License** - see the [LICENSE](./LICENSE) file for full details.

**Summary:**

- ✅ Code available for educational purposes and review
- ✅ May be used for learning and portfolio demonstration
- ❌ Commercial use prohibited without explicit permission
- ❌ Cannot be sold or offered as SaaS without authorization

For commercial licensing inquiries: donatocorbacio92@gmail.com

---

## ⚠️ Disclaimer

This is a **portfolio/demonstration project** showcasing modern Spring Boot development practices.

**Important Notes:**

- This repository contains NO sensitive data (all dummy/example data)
- Configuration uses environment variables (`.env` file git-ignored)
- Not intended for production use without proper security audit
- Sample data is for testing purposes only
- Legacy SQL files in `sql/` folder are for reference only (use Flyway migrations)

**Before production deployment:**

- Perform comprehensive security review
- Conduct penetration testing
- Review data protection compliance (GDPR, etc.)
- Set up proper monitoring and alerting
- Implement rate limiting and DDoS protection
- Verify all Flyway migrations are tested

---

## 🙏 Acknowledgments

Built with modern technologies and best practices from:

- Spring Framework ecosystem
- Java and JVM community
- Flyway database migration tool
- Open-source contributors worldwide

Special thanks to the developers of all libraries and tools used in this project.

---

## 🔗 Related Projects

- **BCM Frontend v2.0** (In Development): React + Next.js frontend
- **BCM v1.0** (Thesis Project): Angular-based original version

---

**⭐ If you're a recruiter or technical reviewer**, feel free to explore the codebase. For questions or to discuss this project in detail, please reach out via email or LinkedIn.

**💬 Open to feedback and collaboration opportunities!**
