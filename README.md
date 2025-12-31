# 🏢 BCM v2.0 - Business Contracts Manager

> Enterprise-grade SaaS platform for contract lifecycle management built with Spring Boot 3.5.9 and Java 21

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-brightgreen?logo=spring)](https://spring.io/projects/spring-boot)
[![Coverage](https://img.shields.io/badge/Coverage-78%25-green)](./target/site/jacoco/index.html)
[![License](https://img.shields.io/badge/License-Custom-blue)](./LICENSE)

## 🎯 Overview

BCM v2.0 is the second iteration of my Business Contract Manager system, representing a complete architectural redesign from the original version developed during my master's thesis. This version showcases modern Spring Boot best practices, comprehensive testing, and production-ready security features.

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

- Spring Boot 3.5.5 (Latest stable)
- Java 21 LTS
- Maven for dependency management

**Database:**

- MySQL 8.0
- Spring Data JPA / Hibernate
- HikariCP connection pooling

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

- JaCoCo (78% test coverage, target: 75%)
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

| Metric               | Value                                  | Status                |
| -------------------- | -------------------------------------- | --------------------- |
| **Test Coverage**    | 78%                                    | ✅ Above target (75%) |
| **Test Cases**       | 32 test classes                        | ✅ Comprehensive      |
| **Security Scan**    | No issues                              | ✅ FindSecBugs pass   |
| **Architecture**     | Clean separation                       | ✅ Professional       |
| **Package Coverage** | auth: 96%, security: 100%, mapper: 98% | ✅ Excellent          |

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
src/main/resources/    # Configuration files (application.properties)
sql/                   # Database schema and sample data
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

```bash
# Login to MySQL
mysql -u root -p

# Create database
CREATE DATABASE bcm CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# Exit MySQL
exit

# Run schema creation script
mysql -u root -p bcm < sql/DDL/bcm_schema.sql

# (Optional) Load sample data for testing
mysql -u root -p bcm < sql/DML/bmc_data.sql
```

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

# Run in development mode
mvn spring-boot:run

# Or specify profile explicitly
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The application will start at: `http://localhost:8090/api/v1`

### 5. Access API Documentation

Once running, visit:

- **Swagger UI:** http://localhost:8090/api/v1/swagger-ui.html
- **OpenAPI JSON:** http://localhost:8090/api/v1/api-docs

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

| Package    | Coverage | Key Tests                   |
| ---------- | -------- | --------------------------- |
| auth       | 96%      | AuthService, AuthController |
| security   | 100%     | SecurityConfig              |
| mapper     | 98%      | All DTO mappings            |
| jwt        | 93%      | Token generation/validation |
| controller | 73%      | REST endpoints              |
| service    | 68%      | Business logic              |

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

- **dev** (default): Development mode with debug logging
- **test**: Testing environment with H2 database
- **prod**: Production mode with optimized settings

### Production Deployment Checklist

Before deploying to production:

- [ ] Generate strong JWT secret (minimum 256 bits, Base64-encoded)
- [ ] Use production database with SSL/TLS
- [ ] Configure `application-prod.properties` with production values
- [ ] Enable HTTPS only
- [ ] Set up automated backups
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
```

---

## 📝 Version History

### Version 2.0 (Current - 2025)

**Major Rewrite:**

- ✨ Migrated to Spring Boot 3.5.5 + Java 21
- ✨ Redesigned architecture with clean layers
- ✨ JWT-based authentication
- ✨ Comprehensive test suite (78% coverage)
- ✨ Role-based access control
- ✨ Email verification system
- ✨ Multi-manager support per contract
- ✨ Advanced search and pagination
- ✨ OpenAPI 3 documentation
- ✨ Production-ready security

### Version 1.0 (2024)

**Original Implementation:**

- Initial version developed during master's thesis
- Angular + Spring Boot + MySQL stack
- Basic CRUD functionality
- Single-manager assignment
- Simple authentication

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

---

## 👨‍💻 About the Developer

**Donato Corbacio**

- 🎓 Master's Degree in Computer Science and Software Production Technologies (Dec 2024)
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

**Before production deployment:**

- Perform comprehensive security review
- Conduct penetration testing
- Review data protection compliance (GDPR, etc.)
- Set up proper monitoring and alerting
- Implement rate limiting and DDoS protection

---

## 🙏 Acknowledgments

Built with modern technologies and best practices from:

- Spring Framework ecosystem
- Java and JVM community
- Open-source contributors worldwide

Special thanks to the developers of all libraries and tools used in this project.

---

## 🔗 Related Projects

- **BCM Frontend v2.0** (In Development): React + Next.js frontend
- **BCM v1.0** (Thesis Project): Angular-based original version

---

**⭐ If you're a recruiter or technical reviewer**, feel free to explore the codebase. For questions or to discuss this project in detail, please reach out via email or LinkedIn.

**💬 Open to feedback and collaboration opportunities!**
