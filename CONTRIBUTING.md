# Contributing to BCM v2.0 Backend

Thank you for your interest in contributing to the Business Contracts Manager backend! 🎉

This is primarily a **portfolio project**, but contributions for bug fixes, improvements, and new features are welcome.

---

## 📋 Table of Contents

- [Code of Conduct](#code-of-conduct)
- [How Can I Contribute?](#how-can-i-contribute)
- [Development Setup](#development-setup)
- [Coding Standards](#coding-standards)
- [Commit Convention](#commit-convention)
- [Pull Request Process](#pull-request-process)
- [Testing Guidelines](#testing-guidelines)
- [Questions?](#questions)

---

## 📜 Code of Conduct

### Our Pledge

Be respectful, inclusive, and professional. This project welcomes contributors of all skill levels.

### Expected Behavior

- ✅ Use welcoming and inclusive language
- ✅ Be respectful of differing viewpoints
- ✅ Accept constructive criticism gracefully
- ✅ Focus on what's best for the project
- ✅ Show empathy towards other community members

### Unacceptable Behavior

- ❌ Harassment or discriminatory language
- ❌ Trolling, insulting comments, or personal attacks
- ❌ Publishing others' private information
- ❌ Other conduct that would be inappropriate in a professional setting

---

## 🤝 How Can I Contribute?

### Reporting Bugs

**Before submitting:**
- Check the [Issues](https://github.com/DonatoCorbacioDev/bcm-v2-backend/issues) to avoid duplicates
- Verify the bug exists in the latest version

**When submitting a bug report, include:**

```markdown
**Describe the bug**
A clear description of what the bug is.

**To Reproduce**
Steps to reproduce:
1. Send POST request to '/api/v1/contracts'
2. With body: { ... }
3. Observe error: ...

**Expected behavior**
What you expected to happen.

**Actual behavior**
What actually happened.

**Environment:**
- Java version: 21
- Spring Boot version: 3.5.10
- OS: Windows 11
- Database: MySQL 8.0

**Logs/Screenshots**
Attach relevant logs or screenshots.
```

---

### Suggesting Enhancements

**Feature requests should include:**
- Clear use case
- Expected behavior
- Potential implementation approach
- Why this benefits the project

**Example:**

```markdown
**Feature Request: Contract Document Upload**

**Use Case:**
Users need to attach PDF contracts to contract records.

**Expected Behavior:**
- POST /api/v1/contracts/{id}/documents
- Accept multipart/form-data
- Store in S3 or local filesystem
- Return document metadata

**Benefits:**
- Complete contract lifecycle management
- Better audit trail
- Industry standard feature

**Potential Implementation:**
- Spring MultipartFile
- AWS S3 SDK or local FileSystem
- Document entity linked to Contract
```

---

### Contributing Code

1. **Fork the repository**
2. **Create a feature branch** from `develop`
3. **Make your changes** following coding standards
4. **Write/update tests** (maintain 100% coverage goal)
5. **Update documentation** if needed
6. **Submit a Pull Request**

---

## 🛠️ Development Setup

### Prerequisites

- Java 21 (JDK)
- Maven 3.8+
- MySQL 8.0+ or H2 (for tests)
- IDE: IntelliJ IDEA, Eclipse, or VS Code

### Initial Setup

```bash
# 1. Fork and clone
git clone https://github.com/YOUR_USERNAME/bcm-v2-backend.git
cd bcm-v2-backend

# 2. Add upstream remote
git remote add upstream https://github.com/DonatoCorbacioDev/bcm-v2-backend.git

# 3. Create database
mysql -u root -p
CREATE DATABASE bcm CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
EXIT;

# 4. Configure environment
cp .env.example .env
# Edit .env with your credentials

# 5. Build and run tests
mvn clean install

# 6. Run application
mvn spring-boot:run
```

### Keeping Your Fork Updated

```bash
# Fetch latest from upstream
git fetch upstream

# Merge into your local main
git checkout main
git merge upstream/main

# Push to your fork
git push origin main
```

---

## 📏 Coding Standards

### General Principles

- **Clean Code:** Follow Robert C. Martin's principles
- **SOLID:** Single responsibility, Open/Closed, Liskov substitution, Interface segregation, Dependency inversion
- **DRY:** Don't Repeat Yourself
- **KISS:** Keep It Simple, Stupid
- **YAGNI:** You Aren't Gonna Need It

### Java Style

- **Formatting:** Follow Google Java Style Guide
- **Naming:**
  - Classes: `PascalCase` (e.g., `ContractService`)
  - Methods/Variables: `camelCase` (e.g., `findById`)
  - Constants: `UPPER_SNAKE_CASE` (e.g., `MAX_PAGE_SIZE`)
  - Packages: `lowercase` (e.g., `com.donatodev.bcm_backend.service`)

- **Structure:**
  ```java
  // Class order:
  1. Static fields
  2. Instance fields (use @Autowired sparingly, prefer constructor injection)
  3. Constructors
  4. Public methods
  5. Private methods
  ```

### Architecture Patterns

**Follow the existing layered architecture:**

```
Controller → Service → Repository → Entity
     ↓          ↓
    DTO ← Mapper
```

**Rules:**
- Controllers should NOT contain business logic
- Services contain all business logic
- Repositories are simple data access (Spring Data JPA)
- DTOs for API contracts, Entities for persistence
- Mappers handle conversions (prefer MapStruct)

### Example: Adding a New Feature

Let's say you want to add a "Contract Categories" feature:

```java
// 1. Entity (domain model)
@Entity
@Table(name = "contract_categories")
public class ContractCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank
    private String name;
    
    private String description;
    
    // Getters, setters, constructors
}

// 2. Repository (data access)
public interface ContractCategoryRepository extends JpaRepository<ContractCategory, Long> {
    Optional<ContractCategory> findByName(String name);
}

// 3. DTO (API contract)
public record ContractCategoryDTO(
    Long id,
    String name,
    String description
) {}

// 4. Mapper (conversion)
@Mapper(componentModel = "spring")
public interface ContractCategoryMapper {
    ContractCategoryDTO toDTO(ContractCategory entity);
    ContractCategory toEntity(ContractCategoryDTO dto);
}

// 5. Service (business logic)
@Service
@Transactional
public class ContractCategoryService {
    private final ContractCategoryRepository repository;
    private final ContractCategoryMapper mapper;
    
    // Constructor injection (preferred)
    public ContractCategoryService(
        ContractCategoryRepository repository,
        ContractCategoryMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }
    
    public List<ContractCategoryDTO> findAll() {
        return repository.findAll()
            .stream()
            .map(mapper::toDTO)
            .toList();
    }
    
    public ContractCategoryDTO create(ContractCategoryDTO dto) {
        if (repository.findByName(dto.name()).isPresent()) {
            throw new DuplicateResourceException("Category already exists");
        }
        ContractCategory entity = mapper.toEntity(dto);
        ContractCategory saved = repository.save(entity);
        return mapper.toDTO(saved);
    }
}

// 6. Controller (REST API)
@RestController
@RequestMapping("/api/v1/contract-categories")
@RequiredArgsConstructor
public class ContractCategoryController {
    private final ContractCategoryService service;
    
    @GetMapping
    public ResponseEntity<List<ContractCategoryDTO>> getAll() {
        return ResponseEntity.ok(service.findAll());
    }
    
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ContractCategoryDTO> create(
        @RequestBody @Valid ContractCategoryDTO dto
    ) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(service.create(dto));
    }
}
```

---

## 📝 Commit Convention

We follow [Conventional Commits](https://www.conventionalcommits.org/) specification.

### Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Types

| Type | Description | Example |
|------|-------------|---------|
| `feat` | New feature | `feat(contracts): add export to PDF` |
| `fix` | Bug fix | `fix(auth): resolve JWT expiration issue` |
| `docs` | Documentation | `docs(readme): update setup instructions` |
| `style` | Code style (formatting, no logic change) | `style(service): reformat with Google style` |
| `refactor` | Code refactoring | `refactor(mapper): simplify DTO conversion logic` |
| `test` | Adding/updating tests | `test(contract): add integration test for search` |
| `chore` | Build process, dependencies | `chore(deps): upgrade Spring Boot to 3.5.10` |
| `perf` | Performance improvement | `perf(query): add index on contract_number` |
| `ci` | CI/CD changes | `ci(github): add automated testing workflow` |
| `revert` | Revert previous commit | `revert: feat(contracts): remove export feature` |

### Scope (Optional)

- `auth` - Authentication/Authorization
- `contracts` - Contract management
- `managers` - Manager management
- `users` - User management
- `email` - Email functionality
- `config` - Configuration
- `security` - Security features
- `db` - Database/Flyway migrations

### Examples

**Good commits:**

```bash
feat(contracts): add search by date range

- Add startDate and endDate parameters to search endpoint
- Update ContractRepository with custom query
- Add integration tests for date filtering

Closes #42

---

fix(auth): prevent JWT token expiration edge case

When token expires during request processing, user receives 500 error.
Now properly catches ExpiredJwtException and returns 401.

Fixes #38

---

docs(api): update Swagger annotations for contract endpoints

- Add @Operation descriptions
- Document request/response examples
- Add error response codes

---

chore(deps): upgrade dependencies to latest versions

- Spring Boot: 3.5.9 → 3.5.10
- MySQL Connector: 8.0.33 → 8.0.39
- JUnit: 5.10.0 → 5.10.3

---

test(mapper): achieve 100% coverage on ContractMapper

Add tests for:
- Null handling
- Empty collections
- Edge cases with optional fields
```

**Bad commits (avoid):**

```bash
❌ Update stuff
❌ Fix bug
❌ WIP
❌ asdfasdf
❌ Fixed it finally!!!
❌ Changes from yesterday
```

---

## 🔍 Pull Request Process

### Before Submitting

**Checklist:**

- [ ] Code follows style guidelines
- [ ] Self-reviewed my code
- [ ] Commented complex logic
- [ ] Updated documentation (README, Javadoc)
- [ ] Added/updated tests (maintain 100% coverage goal)
- [ ] All tests pass (`mvn test`)
- [ ] No linter errors (`mvn spotbugs:check`)
- [ ] No security vulnerabilities (`mvn dependency-check:check`)
- [ ] Commit messages follow convention
- [ ] Branch is up-to-date with `develop`

### Submitting

1. **Push to your fork:**
   ```bash
   git push origin feature/your-feature-name
   ```

2. **Open Pull Request:**
   - Base: `develop` (not `main`)
   - Title: Use conventional commit format
   - Description: Use the template below

3. **PR Description Template:**

```markdown
## Description
Brief description of changes.

## Type of Change
- [ ] Bug fix (non-breaking change fixing an issue)
- [ ] New feature (non-breaking change adding functionality)
- [ ] Breaking change (fix or feature causing existing functionality to break)
- [ ] Documentation update

## Motivation and Context
Why is this change required? What problem does it solve?
Closes #(issue_number)

## How Has This Been Tested?
- [ ] Unit tests
- [ ] Integration tests
- [ ] Manual testing

## Screenshots (if applicable)
[Attach screenshots for UI changes]

## Checklist
- [ ] My code follows the style guidelines
- [ ] I have performed a self-review
- [ ] I have commented my code where necessary
- [ ] I have updated the documentation
- [ ] My changes generate no new warnings
- [ ] I have added tests that prove my fix/feature works
- [ ] New and existing unit tests pass locally
- [ ] Test coverage remains at 100% (or close)
```

### Review Process

1. **Automated checks run** (tests, linters, security scans)
2. **Code review** by maintainers
3. **Feedback addressed** by contributor
4. **Approval** from at least one maintainer
5. **Merge** to `develop` branch

### After Merge

- Your feature will be included in the next release
- You'll be credited in release notes
- Thank you for contributing! 🎉

---

## 🧪 Testing Guidelines

### Test Coverage Goal

**Aim for 100% coverage** on:
- All service methods (business logic)
- All controller endpoints
- All mappers
- Custom repository queries
- Exception handlers

**It's okay to skip:**
- Getters/setters (Lombok-generated)
- Entities (simple data classes)
- Configuration classes (Spring-managed)

### Test Structure

Use **AAA pattern** (Arrange, Act, Assert):

```java
@Test
@DisplayName("Should throw exception when contract not found")
void shouldThrowExceptionWhenContractNotFound() {
    // Arrange
    Long nonExistentId = 999L;
    when(contractRepository.findById(nonExistentId))
        .thenReturn(Optional.empty());
    
    // Act & Assert
    assertThrows(
        ResourceNotFoundException.class,
        () -> contractService.findById(nonExistentId)
    );
}
```

### Test Naming

**Convention:** `should[ExpectedResult]When[Condition]`

```java
@Test
void shouldReturnContractWhenValidIdProvided() { ... }

@Test
void shouldThrowExceptionWhenContractNumberIsDuplicate() { ... }

@Test
void shouldReturnEmptyListWhenNoContractsExist() { ... }
```

### Running Tests

```bash
# All tests
mvn test

# Specific test class
mvn test -Dtest=ContractServiceTest

# Specific test method
mvn test -Dtest=ContractServiceTest#shouldReturnContractWhenValidIdProvided

# With coverage report
mvn clean test jacoco:report
open target/site/jacoco/index.html
```

### Integration Tests

Use `@SpringBootTest` for full application context:

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContractControllerIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldCreateContractWhenValidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/contracts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "contractNumber": "C-2025-001",
                        "customerName": "Acme Corp",
                        "startDate": "2025-01-01",
                        "endDate": "2025-12-31"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.contractNumber").value("C-2025-001"));
    }
}
```

---

## ❓ Questions?

### Need Help?

- 📧 **Email:** donatocorbacio92@gmail.com
- 💼 **LinkedIn:** [Donato Corbacio](https://www.linkedin.com/in/donato-corbacio/)
- 🐛 **Issues:** [GitHub Issues](https://github.com/DonatoCorbacioDev/bcm-v2-backend/issues)

### Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Spring Data JPA Guide](https://spring.io/guides/gs/accessing-data-jpa/)
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [Conventional Commits](https://www.conventionalcommits.org/)

---

## 🙏 Thank You!

Your contributions make this project better for everyone. Whether it's:
- 🐛 Reporting a bug
- 💡 Suggesting a feature
- 📝 Improving documentation
- 💻 Contributing code

**Every contribution is valued!** ❤️

---

**Happy Coding!** 🚀
