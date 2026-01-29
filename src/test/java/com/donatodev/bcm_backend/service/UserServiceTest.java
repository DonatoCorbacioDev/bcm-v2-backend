package com.donatodev.bcm_backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.dto.UserDTO;
import com.donatodev.bcm_backend.dto.UserProfileDTO;
import com.donatodev.bcm_backend.entity.InviteToken;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.PasswordResetToken;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.UserNotFoundException;
import com.donatodev.bcm_backend.mapper.UserMapper;
import com.donatodev.bcm_backend.repository.InviteTokenRepository;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

/**
 * Unit tests for {@link UserService}.
 * <p>
 * This class verifies core user management logic, including:
 * <ul>
 * <li>Retrieving all users and individual users by ID</li>
 * <li>Creating, updating, and deleting users</li>
 * <li>Fetching the current authenticated user's profile</li>
 * <li>Updating user passwords and validating uniqueness constraints</li>
 * <li>Finding users by manager email</li>
 * </ul>
 * <p>
 * Uses {@code Mockito} for mocking dependencies and simulates security context
 * via {@link SecurityContextHolder} for role-based operations.
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class UserServiceTest {

    @Mock
    private UsersRepository usersRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ManagersRepository managersRepository;

    @Mock
    private RolesRepository rolesRepository;

    @Mock
    private InviteTokenRepository inviteTokenRepository;

    @Mock
    private PasswordResetTokenService passwordResetTokenService;

    @Mock
    private IEmailService emailService;

    @InjectMocks
    private UserService userService;

    @AfterEach
    @SuppressWarnings("unused")
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: UserService")
    @SuppressWarnings("unused")
    class VerifyUserService {

        /**
         * Test: Retrieve all users from repository and convert to DTO list.
         * Verifies correct mapping and list size.
         */
        @Test
        @Order(1)
        @DisplayName("Get all users")
        void shouldGetAllUsers() {
            Users user = Users.builder().id(1L).username("admin").build();
            UserDTO dto = new UserDTO(1L, "admin", "pass", 1L, 1L, null, null);

            when(usersRepository.findAll()).thenReturn(List.of(user));
            when(userMapper.toDTO(user)).thenReturn(dto);

            List<UserDTO> result = userService.getAllUsers();

            assertEquals(1, result.size());
            assertEquals("admin", result.get(0).username());
        }

        /**
         * Test: Retrieve a single user by ID and map to DTO. Verifies username
         * is correctly returned.
         */
        @Test
        @Order(2)
        @DisplayName("Get user by ID")
        void shouldGetUserById() {
            Users user = Users.builder().id(1L).username("admin").build();
            UserDTO dto = new UserDTO(1L, "admin", "pass", 1L, 1L, null, null);

            when(usersRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userMapper.toDTO(user)).thenReturn(dto);

            UserDTO result = userService.getUserById(1L);

            assertEquals("admin", result.username());
        }

        /**
         * Test: Throw UserNotFoundException when user ID does not exist.
         */
        @Test
        @Order(3)
        @DisplayName("Get user by ID throws if not found")
        void shouldThrowIfUserNotFound() {
            when(usersRepository.findById(999L)).thenReturn(Optional.empty());
            UserNotFoundException ex
                    = assertThrows(UserNotFoundException.class, () -> userService.getUserById(999L));
            assertEquals("User ID 999 not found", ex.getMessage());
        }

        /**
         * Test: Successfully create a new user. Ensures username uniqueness,
         * password encoding, and correct DTO result.
         */
        @Test
        @Order(4)
        @DisplayName("Create user successfully")
        void shouldCreateUser() {
            UserDTO dto = new UserDTO(null, "user1", "plainpass", 1L, 1L, null, null);
            Users user = Users.builder().username("user1").build();
            Users saved = Users.builder().id(1L).username("user1").build();
            UserDTO savedDTO = new UserDTO(1L, "user1", null, 1L, 1L, null, null);

            when(usersRepository.existsByUsername("user1")).thenReturn(false);
            when(userMapper.toEntity(dto)).thenReturn(user);
            when(passwordEncoder.encode("plainpass")).thenReturn("encodedpass");
            when(usersRepository.save(user)).thenReturn(saved);
            when(userMapper.toDTO(saved)).thenReturn(savedDTO);

            UserDTO result = userService.createUser(dto);

            assertEquals(1L, result.id());
            verify(usersRepository).save(user);
        }

        /**
         * Test: Update an existing user with new data and return updated DTO.
         * Verifies manager and role associations.
         */
        @Test
        @Order(5)
        @DisplayName("Update user successfully")
        void shouldUpdateUser() {
            Users user = Users.builder().id(1L).username("olduser").build();
            Managers manager = Managers.builder().id(2L).build();
            Roles role = Roles.builder().id(3L).role("MANAGER").build();
            UserDTO dto = new UserDTO(1L, "updatedUser", "newpass", 2L, 3L, null, null);
            UserDTO resultDTO = new UserDTO(1L, "updatedUser", null, 2L, 3L, null, null);

            when(usersRepository.findById(1L)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode("newpass")).thenReturn("encodedpass");
            when(managersRepository.findById(2L)).thenReturn(Optional.of(manager));
            when(rolesRepository.findById(3L)).thenReturn(Optional.of(role));
            when(usersRepository.save(user)).thenReturn(user);
            when(userMapper.toDTO(user)).thenReturn(resultDTO);

            UserDTO result = userService.updateUser(1L, dto);

            assertEquals("updatedUser", result.username());
            verify(usersRepository).save(user);
        }

        /**
         * Test: Delete a user by ID. Verifies repository delete method is
         * called.
         */
        @Test
        @Order(6)
        @DisplayName("Delete user by ID")
        void shouldDeleteUser() {
            userService.deleteUser(1L);
            verify(usersRepository).deleteById(1L);
        }

        /**
         * Test: Retrieve the currently authenticated user's profile. Simulates
         * security context and validates role and verification flag.
         */
        @Test
        @Order(7)
        @DisplayName("Get current user profile")
        void shouldReturnCurrentUserProfile() {
            Users user = Users.builder()
                    .id(1L)
                    .username("admin")
                    .role(Roles.builder().role("ADMIN").build())
                    .verified(true)
                    .build();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("admin", null)
            );

            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(user));

            UserProfileDTO result = userService.getCurrentUserProfile();

            assertEquals("admin", result.username());
            assertEquals("ADMIN", result.role());
            assertTrue(result.verified());
        }

        /**
         * Test: Find user by associated manager email address. Verifies correct
         * filtering among all users.
         */
        @Test
        @Order(8)
        @DisplayName("Find user by manager email")
        void shouldFindUserByManagerEmail() {
            Users user = Users.builder()
                    .id(1L)
                    .username("managerUser")
                    .manager(Managers.builder().email("email@example.com").build())
                    .build();

            when(usersRepository.findAll()).thenReturn(List.of(user));

            Optional<Users> result = userService.findByEmail("email@example.com");

            assertTrue(result.isPresent());
            assertEquals("managerUser", result.get().getUsername());
        }

        /**
         * Test: Update a user's password and persist the change. Verifies
         * password encoding and repository save.
         */
        @Test
        @Order(9)
        @DisplayName("Update user password")
        void shouldUpdateUserPassword() {
            Users user = Users.builder()
                    .id(1L)
                    .username("user")
                    .build();

            when(passwordEncoder.encode("newSecret")).thenReturn("encodedSecret");

            userService.updatePassword(user, "newSecret");

            assertEquals("encodedSecret", user.getPasswordHash());
            verify(usersRepository).save(user);
        }

        /**
         * Test: Throw exception when trying to register with an existing
         * username.
         */
        @Test
        @Order(10)
        @DisplayName("Register user fails if username exists")
        void shouldThrowIfUsernameExists() {
            UserDTO dto = new UserDTO(null, "existingUser", "pass", null, null, null, null);
            when(usersRepository.existsByUsername("existingUser")).thenReturn(true);

            IllegalArgumentException ex
                    = assertThrows(IllegalArgumentException.class, () -> userService.createUser(dto));
            assertEquals("Username already exists.", ex.getMessage());
        }

        /**
         * Test: Should throw exception if manager ID is already assigned to
         * another user during registration.
         */
        @Test
        @Order(11)
        @DisplayName("Register user fails if manager already assigned")
        void shouldThrowIfManagerAlreadyAssigned() {
            UserDTO dto = new UserDTO(null, "newuser", "pass", 1L, null, null, null);
            when(usersRepository.existsByUsername("newuser")).thenReturn(false);
            when(usersRepository.existsByManagerId(1L)).thenReturn(true);

            IllegalArgumentException ex
                    = assertThrows(IllegalArgumentException.class, () -> userService.createUser(dto));
            assertEquals("This manager is already associated with another user.", ex.getMessage());
        }

        /**
         * Test: Should throw exception if manager is not found during user
         * update.
         */
        @Test
        @Order(12)
        @DisplayName("Update user fails if manager not found")
        void shouldThrowIfManagerNotFound() {
            UserDTO dto = new UserDTO(1L, "user", "pass", 10L, 1L, null, null);
            Users user = Users.builder().id(1L).build();

            when(usersRepository.findById(1L)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode("pass")).thenReturn("encoded");
            when(managersRepository.findById(10L)).thenReturn(Optional.empty());

            IllegalArgumentException ex
                    = assertThrows(IllegalArgumentException.class, () -> userService.updateUser(1L, dto));
            assertEquals("Manager ID not found", ex.getMessage());
        }

        /**
         * Test: Should throw exception if role is not found during user update.
         */
        @Test
        @Order(13)
        @DisplayName("Update user fails if role not found")
        void shouldThrowIfRoleNotFound() {
            UserDTO dto = new UserDTO(1L, "user", "pass", 1L, 20L, null, null);
            Users user = Users.builder().id(1L).build();
            Managers manager = Managers.builder().id(1L).build();

            when(usersRepository.findById(1L)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode("pass")).thenReturn("encoded");
            when(managersRepository.findById(1L)).thenReturn(Optional.of(manager));
            when(rolesRepository.findById(20L)).thenReturn(Optional.empty());

            IllegalArgumentException ex
                    = assertThrows(IllegalArgumentException.class, () -> userService.updateUser(1L, dto));
            assertEquals("Role ID not found", ex.getMessage());
        }

        /**
         * Test: Should throw exception if current authenticated user is not
         * found in the database.
         */
        @Test
        @Order(14)
        @DisplayName("Get current user profile fails if user not found")
        void shouldThrowIfCurrentUserNotFound() {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("ghost", null)
            );

            when(usersRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            UsernameNotFoundException ex
                    = assertThrows(UsernameNotFoundException.class, () -> userService.getCurrentUserProfile());
            assertEquals("User not found: ghost", ex.getMessage());
        }

        /**
         * Test: Save user entity directly using repository.
         */
        @Test
        @Order(15)
        @DisplayName("Save user directly")
        void shouldSaveUserDirectly() {
            Users user = Users.builder().username("saveuser").build();
            userService.save(user);
            verify(usersRepository).save(user);
        }

        /**
         * Test: Should return empty when no user's manager has the given email.
         */
        @Test
        @Order(16)
        @DisplayName("Find user by manager email returns empty if not found")
        void shouldReturnEmptyIfManagerEmailNotFound() {
            Users user = Users.builder()
                    .id(1L)
                    .username("managerUser")
                    .manager(Managers.builder().email("other@example.com").build())
                    .build();

            when(usersRepository.findAll()).thenReturn(List.of(user));

            Optional<Users> result = userService.findByEmail("notfound@example.com");

            assertTrue(result.isEmpty());
        }

        /**
         * Test: Successfully register user when manager ID is provided and not
         * yet assigned.
         */
        @Test
        @Order(17)
        @DisplayName("Register user passes if managerId is not null and not assigned")
        void shouldRegisterUserIfManagerIdNotAssigned() {
            UserDTO dto = new UserDTO(null, "newUser", "password", 1L, 2L, null, null);
            Users user = Users.builder().username("newUser").build();
            Users saved = Users.builder().id(1L).username("newUser").build();
            UserDTO savedDTO = new UserDTO(1L, "newUser", null, 1L, 2L, null, null);

            when(usersRepository.existsByUsername("newUser")).thenReturn(false);
            when(usersRepository.existsByManagerId(1L)).thenReturn(false);
            when(userMapper.toEntity(dto)).thenReturn(user);
            when(passwordEncoder.encode("password")).thenReturn("encodedPassword");
            when(usersRepository.save(user)).thenReturn(saved);
            when(userMapper.toDTO(saved)).thenReturn(savedDTO);

            UserDTO result = userService.createUser(dto);

            assertEquals("newUser", result.username());
            verify(usersRepository).save(user);
        }

        /**
         * Test: Should skip user in findByEmail() if user has no manager
         * assigned.
         */
        @Test
        @Order(18)
        @DisplayName("Find user by email where manager is null")
        void shouldSkipUserIfManagerIsNull() {
            Users user = Users.builder()
                    .id(1L)
                    .username("userWithoutManager")
                    .manager(null)
                    .build();

            when(usersRepository.findAll()).thenReturn(List.of(user));

            Optional<Users> result = userService.findByEmail("email@example.com");

            assertTrue(result.isEmpty());
        }

        /**
         * Test: Should skip user in findByEmail() if manager's email is null.
         */
        @Test
        @Order(19)
        @DisplayName("Find by email should skip users with null manager email")
        void shouldSkipUserIfManagerEmailIsNull() {
            Users user = Users.builder()
                    .id(2L)
                    .username("nullEmailManager")
                    .manager(Managers.builder().email(null).build())
                    .build();

            when(usersRepository.findAll()).thenReturn(List.of(user));

            Optional<Users> result = userService.findByEmail("email@example.com");

            assertTrue(result.isEmpty());
        }

        /**
         * Test: Successfully register user when manager ID is null.
         */
        @Test
        @Order(20)
        @DisplayName("Register user when managerId is null")
        void shouldRegisterUserIfManagerIdIsNull() {
            UserDTO dto = new UserDTO(null, "userNoManager", "pass", null, 2L, null, null);
            Users user = Users.builder().username("userNoManager").build();
            Users saved = Users.builder().id(1L).username("userNoManager").build();
            UserDTO savedDTO = new UserDTO(1L, "userNoManager", null, null, 2L, null, null);

            when(usersRepository.existsByUsername("userNoManager")).thenReturn(false);
            when(userMapper.toEntity(dto)).thenReturn(user);
            when(passwordEncoder.encode("pass")).thenReturn("encodedPass");
            when(usersRepository.save(user)).thenReturn(saved);
            when(userMapper.toDTO(saved)).thenReturn(savedDTO);

            UserDTO result = userService.createUser(dto);

            assertEquals("userNoManager", result.username());
            verify(usersRepository).save(user);
        }

        /**
         * Test: Should throw UserNotFoundException with correct message when
         * user ID does not exist.
         */
        @Test
        @Order(21)
        @DisplayName("Get user by ID throws with correct message")
        void shouldThrowUserNotFoundWithCorrectMessage() {
            long userId = 123L;
            when(usersRepository.findById(userId)).thenReturn(Optional.empty());

            UserNotFoundException ex = assertThrows(UserNotFoundException.class, () -> userService.getUserById(userId));
            assertEquals("User ID 123 not found", ex.getMessage());
        }

        /**
         * Test: Should throw UserNotFoundException with correct message during
         * update when user ID is not found.
         */
        @Test
        @Order(22)
        @DisplayName("Update user throws with correct UserNotFoundException message")
        void shouldThrowUserNotFoundWithCorrectMessageInUpdate() {
            long userId = 999L;
            UserDTO dto = new UserDTO(userId, "updatedUser", "pass", 1L, 1L, null, null);

            when(usersRepository.findById(userId)).thenReturn(Optional.empty());

            UserNotFoundException exception = assertThrows(UserNotFoundException.class, () -> userService.updateUser(userId, dto));

            assertEquals("User ID 999 not found", exception.getMessage());
        }

        /**
         * Test: Successfully invite a user with MANAGER role and generate
         * invite link.
         */
        @Test
        @Order(23)
        @DisplayName("Invite user successfully generates token and link")
        void shouldInviteUserSuccessfully() {
            Managers manager = Managers.builder().id(1L).email("manager@company.com").build();

            when(usersRepository.existsByUsername("newmanager")).thenReturn(false);
            when(managersRepository.findById(1L)).thenReturn(Optional.of(manager));

            String result = userService.inviteUser("newmanager", "MANAGER", 1L);

            assertTrue(result.contains("/complete-invite?token="));
            verify(inviteTokenRepository).save(any(InviteToken.class));
        }

        /**
         * Test: Should throw exception if username already exists during
         * invite.
         */
        @Test
        @Order(24)
        @DisplayName("Invite user fails if username already exists")
        void shouldThrowIfUsernameExistsDuringInvite() {
            when(usersRepository.existsByUsername("existinguser")).thenReturn(true);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> userService.inviteUser("existinguser", "MANAGER", 1L));
            assertEquals("Username already exists.", ex.getMessage());
        }

        /**
         * Test: Should throw exception if role is not MANAGER during invite.
         */
        @Test
        @Order(25)
        @DisplayName("Invite user fails if role is not MANAGER")
        void shouldThrowIfRoleNotManagerDuringInvite() {
            when(usersRepository.existsByUsername("user")).thenReturn(false);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> userService.inviteUser("user", "USER", 1L));

            assertEquals("Only MANAGER role allowed via invite.", ex.getMessage());

        }

        /**
         * Test: Should throw exception if manager is not found during invite.
         */
        @Test
        @Order(26)
        @DisplayName("Invite user fails if manager not found")
        void shouldThrowIfManagerNotFoundDuringInvite() {
            when(usersRepository.existsByUsername("user")).thenReturn(false);
            when(managersRepository.findById(999L)).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> userService.inviteUser("user", "MANAGER", 999L));

            assertEquals("Manager not found", ex.getMessage());
        }

        /**
         * Test: Successfully complete invite and create new user.
         */
        @Test
        @Order(27)
        @DisplayName("Complete invite successfully creates new user")
        void shouldCompleteInviteSuccessfully() {
            String token = "valid-token-123";
            Managers manager = Managers.builder().id(1L).email("mgr@company.com").build();
            Roles role = Roles.builder().role("MANAGER").build();

            InviteToken inviteToken = InviteToken.builder()
                    .token(token)
                    .username("inviteduser")
                    .role("MANAGER")
                    .managerId(1L)
                    .expiryDate(LocalDateTime.now().plusHours(24))
                    .used(false)
                    .build();

            when(inviteTokenRepository.findByToken(token)).thenReturn(Optional.of(inviteToken));
            when(usersRepository.existsByUsername("inviteduser")).thenReturn(false);
            when(usersRepository.existsByManagerId(1L)).thenReturn(false);
            when(managersRepository.findById(1L)).thenReturn(Optional.of(manager));
            when(rolesRepository.findByRole("MANAGER")).thenReturn(Optional.of(role));
            when(passwordEncoder.encode("mypassword")).thenReturn("encodedpass");

            userService.completeInvite(token, "mypassword");

            verify(usersRepository).save(any(Users.class));
            verify(inviteTokenRepository).save(any(InviteToken.class));
            assertTrue(inviteToken.isUsed());
        }

        /**
         * Test: Should throw exception if invite token is invalid.
         */
        @Test
        @Order(28)
        @DisplayName("Complete invite fails if token is invalid")
        void shouldThrowIfInviteTokenInvalid() {
            when(inviteTokenRepository.findByToken("invalid")).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> userService.completeInvite("invalid", "password"));

            assertEquals("Invalid invite token", ex.getMessage());
        }

        /**
         * Test: Should throw exception if invite token is already used.
         */
        @Test
        @Order(29)
        @DisplayName("Complete invite fails if token already used")
        void shouldThrowIfInviteTokenAlreadyUsed() {
            InviteToken usedToken = InviteToken.builder()
                    .token("used-token")
                    .username("user")
                    .used(true)
                    .expiryDate(LocalDateTime.now().plusHours(24))
                    .build();

            when(inviteTokenRepository.findByToken("used-token")).thenReturn(Optional.of(usedToken));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> userService.completeInvite("used-token", "password"));

            assertEquals("Invite token used or expired", ex.getMessage());
        }

        /**
         * Test: Should throw exception if invite token is expired.
         */
        @Test
        @Order(30)
        @DisplayName("Complete invite fails if token expired")
        void shouldThrowIfInviteTokenExpired() {
            InviteToken expiredToken = InviteToken.builder()
                    .token("expired-token")
                    .username("user")
                    .used(false)
                    .expiryDate(LocalDateTime.now().minusHours(1))
                    .build();

            when(inviteTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(expiredToken));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> userService.completeInvite("expired-token", "password"));

            assertEquals("Invite token used or expired", ex.getMessage());
        }

        /**
         * Test: Should throw exception if username already exists during
         * complete invite.
         */
        @Test
        @Order(31)
        @DisplayName("Complete invite fails if username exists")
        void shouldThrowIfUsernameExistsOnCompleteInvite() {
            InviteToken inviteToken = InviteToken.builder()
                    .token("token")
                    .username("existinguser")
                    .used(false)
                    .expiryDate(LocalDateTime.now().plusHours(24))
                    .build();

            when(inviteTokenRepository.findByToken("token")).thenReturn(Optional.of(inviteToken));
            when(usersRepository.existsByUsername("existinguser")).thenReturn(true);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> userService.completeInvite("token", "password"));

            assertEquals("User already exists.", ex.getMessage());
        }

        /**
         * Test: Should throw exception if manager already assigned during
         * complete invite.
         */
        @Test
        @Order(32)
        @DisplayName("Complete invite fails if manager already assigned")
        void shouldThrowIfManagerAlreadyAssignedOnCompleteInvite() {
            InviteToken inviteToken = InviteToken.builder()
                    .token("token")
                    .username("newuser")
                    .managerId(1L)
                    .used(false)
                    .expiryDate(LocalDateTime.now().plusHours(24))
                    .build();

            when(inviteTokenRepository.findByToken("token")).thenReturn(Optional.of(inviteToken));
            when(usersRepository.existsByUsername("newuser")).thenReturn(false);
            when(usersRepository.existsByManagerId(1L)).thenReturn(true);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> userService.completeInvite("token", "password"));

            assertEquals("This manager is already associated with another user.", ex.getMessage());
        }

        /**
         * Test: Update user partial - update only username successfully.
         */
        @Test
        @Order(33)
        @DisplayName("Update user partial - update username only")
        void shouldUpdateUsernameOnlyInPartialUpdate() {
            Users user = Users.builder()
                    .id(1L)
                    .username("oldusername")
                    .passwordHash("oldpass")
                    .build();

            when(usersRepository.findById(1L)).thenReturn(Optional.of(user));
            when(usersRepository.existsByUsername("newusername")).thenReturn(false);
            when(usersRepository.save(user)).thenReturn(user);

            Users result = userService.updateUserPartial(1L, "newusername", null, null, null);

            assertEquals("newusername", result.getUsername());
            verify(usersRepository).save(user);
        }

        /**
         * Test: Update user partial - fails if new username already exists.
         */
        @Test
        @Order(34)
        @DisplayName("Update user partial - fails if username exists")
        void shouldThrowIfUsernameExistsInPartialUpdate() {
            Users user = Users.builder()
                    .id(1L)
                    .username("olduser")
                    .build();

            when(usersRepository.findById(1L)).thenReturn(Optional.of(user));
            when(usersRepository.existsByUsername("existinguser")).thenReturn(true);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> userService.updateUserPartial(1L, "existinguser", null, null, null));

            assertEquals("Username already exists.", ex.getMessage());
        }

        /**
         * Test: Update user partial - skip username update if same as current.
         */
        @Test
        @Order(35)
        @DisplayName("Update user partial - skip if username unchanged")
        void shouldSkipUsernameUpdateIfUnchanged() {
            Users user = Users.builder()
                    .id(1L)
                    .username("sameuser")
                    .build();

            when(usersRepository.findById(1L)).thenReturn(Optional.of(user));
            when(usersRepository.save(user)).thenReturn(user);

            Users result = userService.updateUserPartial(1L, "sameuser", null, null, null);

            assertEquals("sameuser", result.getUsername());
            verify(usersRepository).save(user);
        }

        /**
         * Test: Update user partial - update only role successfully.
         */
        @Test
        @Order(36)
        @DisplayName("Update user partial - update role only")
        void shouldUpdateRoleOnlyInPartialUpdate() {
            Users user = Users.builder().id(1L).username("user").build();
            Roles role = Roles.builder().id(2L).role("ADMIN").build();

            when(usersRepository.findById(1L)).thenReturn(Optional.of(user));
            when(rolesRepository.findByRole("ADMIN")).thenReturn(Optional.of(role));
            when(usersRepository.save(user)).thenReturn(user);

            Users result = userService.updateUserPartial(1L, null, "ADMIN", null, null);

            assertEquals(role, result.getRole());
            verify(usersRepository).save(user);
        }

        /**
         * Test: Update user partial - fails if role not found.
         */
        @Test
        @Order(37)
        @DisplayName("Update user partial - fails if role not found")
        void shouldThrowIfRoleNotFoundInPartialUpdate() {
            Users user = Users.builder().id(1L).username("user").build();

            when(usersRepository.findById(1L)).thenReturn(Optional.of(user));
            when(rolesRepository.findByRole("INVALIDROLE")).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> userService.updateUserPartial(1L, null, "INVALIDROLE", null, null));

            assertEquals("Role not found: INVALIDROLE", ex.getMessage());
        }

        /**
         * Test: Update user partial - update only manager successfully.
         */
        @Test
        @Order(38)
        @DisplayName("Update user partial - update manager only")
        void shouldUpdateManagerOnlyInPartialUpdate() {
            Users user = Users.builder().id(1L).username("user").build();
            Managers manager = Managers.builder().id(5L).build();

            when(usersRepository.findById(1L)).thenReturn(Optional.of(user));
            when(managersRepository.findById(5L)).thenReturn(Optional.of(manager));
            when(usersRepository.save(user)).thenReturn(user);

            Users result = userService.updateUserPartial(1L, null, null, 5L, null);

            assertEquals(manager, result.getManager());
            verify(usersRepository).save(user);
        }

        /**
         * Test: Update user partial - fails if manager not found.
         */
        @Test
        @Order(39)
        @DisplayName("Update user partial - fails if manager not found")
        void shouldThrowIfManagerNotFoundInPartialUpdate() {
            Users user = Users.builder().id(1L).username("user").build();

            when(usersRepository.findById(1L)).thenReturn(Optional.of(user));
            when(managersRepository.findById(999L)).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> userService.updateUserPartial(1L, null, null, 999L, null));

            assertEquals("Manager ID not found", ex.getMessage());
        }

        /**
         * Test: Update user partial - update only password successfully.
         */
        @Test
        @Order(40)
        @DisplayName("Update user partial - update password only")
        void shouldUpdatePasswordOnlyInPartialUpdate() {
            Users user = Users.builder().id(1L).username("user").build();

            when(usersRepository.findById(1L)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode("newpassword")).thenReturn("encodednewpass");
            when(usersRepository.save(user)).thenReturn(user);

            Users result = userService.updateUserPartial(1L, null, null, null, "newpassword");

            assertEquals("encodednewpass", result.getPasswordHash());
            verify(usersRepository).save(user);
        }

        /**
         * Test: Update user partial - user not found throws exception.
         */
        @Test
        @Order(41)
        @DisplayName("Update user partial - fails if user not found")
        void shouldThrowIfUserNotFoundInPartialUpdate() {
            when(usersRepository.findById(999L)).thenReturn(Optional.empty());

            UserNotFoundException ex = assertThrows(UserNotFoundException.class,
                    () -> userService.updateUserPartial(999L, "newname", null, null, null));

            assertEquals("User ID 999 not found", ex.getMessage());
        }

        /**
         * Test: Update user partial - skip username if null.
         */
        @Test
        @Order(42)
        @DisplayName("Update user partial - skip username if null")
        void shouldSkipUsernameIfNull() {
            Users user = Users.builder().id(1L).username("original").build();

            when(usersRepository.findById(1L)).thenReturn(Optional.of(user));
            when(usersRepository.save(user)).thenReturn(user);

            Users result = userService.updateUserPartial(1L, null, null, null, null);

            assertEquals("original", result.getUsername());
        }

        /**
         * Test: Update user partial - skip username if blank.
         */
        @Test
        @Order(43)
        @DisplayName("Update user partial - skip username if blank")
        void shouldSkipUsernameIfBlank() {
            Users user = Users.builder().id(1L).username("original").build();

            when(usersRepository.findById(1L)).thenReturn(Optional.of(user));
            when(usersRepository.save(user)).thenReturn(user);

            Users result = userService.updateUserPartial(1L, "   ", null, null, null);

            assertEquals("original", result.getUsername());
        }

        /**
         * Test: Update user partial - skip role if blank.
         */
        @Test
        @Order(44)
        @DisplayName("Update user partial - skip role if blank")
        void shouldSkipRoleIfBlank() {
            Users user = Users.builder().id(1L).username("user").build();

            when(usersRepository.findById(1L)).thenReturn(Optional.of(user));
            when(usersRepository.save(user)).thenReturn(user);

            userService.updateUserPartial(1L, null, "  ", null, null);

            verify(rolesRepository, never()).findByRole(any());
        }

        /**
         * Test: Update user partial - skip password if blank.
         */
        @Test
        @Order(45)
        @DisplayName("Update user partial - skip password if blank")
        void shouldSkipPasswordIfBlank() {
            Users user = Users.builder().id(1L).username("user").build();

            when(usersRepository.findById(1L)).thenReturn(Optional.of(user));
            when(usersRepository.save(user)).thenReturn(user);

            userService.updateUserPartial(1L, null, null, null, "   ");

            verify(passwordEncoder, never()).encode(any());
        }

        /**
         * Test: Update user partial - update all fields at once.
         */
        @Test
        @Order(46)
        @DisplayName("Update user partial - update all fields")
        void shouldUpdateAllFieldsInPartialUpdate() {
            Users user = Users.builder().id(1L).username("olduser").build();
            Roles role = Roles.builder().id(2L).role("MANAGER").build();
            Managers manager = Managers.builder().id(3L).build();

            when(usersRepository.findById(1L)).thenReturn(Optional.of(user));
            when(usersRepository.existsByUsername("newuser")).thenReturn(false);
            when(rolesRepository.findByRole("MANAGER")).thenReturn(Optional.of(role));
            when(managersRepository.findById(3L)).thenReturn(Optional.of(manager));
            when(passwordEncoder.encode("newpass")).thenReturn("encodedpass");
            when(usersRepository.save(user)).thenReturn(user);

            Users result = userService.updateUserPartial(1L, "newuser", "MANAGER", 3L, "newpass");

            assertEquals("newuser", result.getUsername());
            assertEquals(role, result.getRole());
            assertEquals(manager, result.getManager());
            assertEquals("encodedpass", result.getPasswordHash());
            verify(usersRepository).save(user);
        }

        /**
         * Test: Send reset link successfully with manager email.
         */
        @Test
        @Order(47)
        @DisplayName("Send reset link with manager email")
        void shouldSendResetLinkWithManagerEmail() {
            Users user = Users.builder()
                    .id(1L)
                    .username("user")
                    .manager(Managers.builder().email("manager@company.com").build())
                    .build();

            PasswordResetToken token = PasswordResetToken.builder()
                    .token("reset-token-123")
                    .build();

            when(usersRepository.findById(1L)).thenReturn(Optional.of(user));
            when(passwordResetTokenService.createToken(user)).thenReturn(token);

            userService.sendResetLink(1L);

            verify(emailService).sendResetPasswordEmail(
                    eq("manager@company.com"),
                    contains("/auth/reset-password?token=reset-token-123")
            );
        }

        /**
         * Test: Send reset link uses username as fallback when manager email is
         * null.
         */
        @Test
        @Order(48)
        @DisplayName("Send reset link uses username as fallback")
        void shouldSendResetLinkToUsernameWhenNoManagerEmail() {
            Users user = Users.builder()
                    .id(1L)
                    .username("john.doe")
                    .manager(null)
                    .build();

            PasswordResetToken token = PasswordResetToken.builder()
                    .token("reset-token-456")
                    .build();

            when(usersRepository.findById(1L)).thenReturn(Optional.of(user));
            when(passwordResetTokenService.createToken(user)).thenReturn(token);

            userService.sendResetLink(1L);

            verify(emailService).sendResetPasswordEmail(
                    eq("john.doe"),
                    contains("/auth/reset-password?token=reset-token-456")
            );
        }

        /**
         * Test: Send reset link uses username when manager exists but email is
         * null.
         */
        @Test
        @Order(49)
        @DisplayName("Send reset link uses username when manager email is null")
        void shouldSendResetLinkToUsernameWhenManagerEmailIsNull() {
            Users user = Users.builder()
                    .id(1L)
                    .username("jane.smith")
                    .manager(Managers.builder().email(null).build())
                    .build();

            PasswordResetToken token = PasswordResetToken.builder()
                    .token("reset-token-789")
                    .build();

            when(usersRepository.findById(1L)).thenReturn(Optional.of(user));
            when(passwordResetTokenService.createToken(user)).thenReturn(token);

            userService.sendResetLink(1L);

            verify(emailService).sendResetPasswordEmail(
                    eq("jane.smith"),
                    contains("/auth/reset-password?token=reset-token-789")
            );
        }

        /**
         * Test: Send reset link fails if user not found.
         */
        @Test
        @Order(50)
        @DisplayName("Send reset link fails if user not found")
        void shouldThrowIfUserNotFoundForResetLink() {
            when(usersRepository.findById(999L)).thenReturn(Optional.empty());

            UserNotFoundException ex = assertThrows(UserNotFoundException.class,
                    () -> userService.sendResetLink(999L));

            assertEquals("User ID 999 not found", ex.getMessage());
        }

        /**
         * Test: Search users with query parameter only.
         */
        @Test
        @Order(51)
        @DisplayName("Search users by query")
        @SuppressWarnings("unchecked")
        void shouldSearchUsersByQuery() {
            Pageable pageable = Pageable.unpaged();
            Users user = Users.builder().id(1L).username("johndoe").build();
            Page<Users> page = new PageImpl<>(List.of(user));

            when(usersRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

            Page<Users> result = userService.searchUsers("john", null, null, pageable);

            assertEquals(1, result.getContent().size());
            verify(usersRepository).findAll(any(Specification.class), eq(pageable));
        }

        /**
         * Test: Search users with role parameter only.
         */
        @Test
        @Order(52)
        @DisplayName("Search users by role")
        @SuppressWarnings("unchecked")
        void shouldSearchUsersByRole() {
            Pageable pageable = Pageable.unpaged();
            Users user = Users.builder().id(1L).username("admin").build();
            Page<Users> page = new PageImpl<>(List.of(user));

            when(usersRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

            Page<Users> result = userService.searchUsers(null, "ADMIN", null, pageable);

            assertEquals(1, result.getContent().size());
            verify(usersRepository).findAll(any(Specification.class), eq(pageable));
        }

        /**
         * Test: Search users with verified parameter only.
         */
        @Test
        @Order(53)
        @DisplayName("Search users by verified status")
        @SuppressWarnings("unchecked")
        void shouldSearchUsersByVerified() {
            Pageable pageable = Pageable.unpaged();
            Users user = Users.builder().id(1L).username("verified").verified(true).build();
            Page<Users> page = new PageImpl<>(List.of(user));

            when(usersRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

            Page<Users> result = userService.searchUsers(null, null, true, pageable);

            assertEquals(1, result.getContent().size());
            verify(usersRepository).findAll(any(Specification.class), eq(pageable));
        }

        /**
         * Test: Search users with all parameters.
         */
        @Test
        @Order(54)
        @DisplayName("Search users with all filters")
        @SuppressWarnings("unchecked")
        void shouldSearchUsersWithAllFilters() {
            Pageable pageable = Pageable.unpaged();
            Users user = Users.builder().id(1L).username("admin").verified(true).build();
            Page<Users> page = new PageImpl<>(List.of(user));

            when(usersRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

            Page<Users> result = userService.searchUsers("adm", "ADMIN", true, pageable);

            assertEquals(1, result.getContent().size());
            verify(usersRepository).findAll(any(Specification.class), eq(pageable));
        }

        /**
         * Test: Search users with no filters (all null).
         */
        @Test
        @Order(55)
        @DisplayName("Search users with no filters")
        @SuppressWarnings("unchecked")
        void shouldSearchUsersWithNoFilters() {
            Pageable pageable = Pageable.unpaged();
            Users user1 = Users.builder().id(1L).username("user1").build();
            Users user2 = Users.builder().id(2L).username("user2").build();
            Page<Users> page = new PageImpl<>(List.of(user1, user2));

            when(usersRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

            Page<Users> result = userService.searchUsers(null, null, null, pageable);

            assertEquals(2, result.getContent().size());
            verify(usersRepository).findAll(any(Specification.class), eq(pageable));
        }

        /**
         * Test: Search users skips blank query.
         */
        @Test
        @Order(56)
        @DisplayName("Search users skips blank query")
        @SuppressWarnings("unchecked")
        void shouldSkipBlankQueryInSearch() {
            Pageable pageable = Pageable.unpaged();
            Page<Users> page = new PageImpl<>(List.of());

            when(usersRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

            userService.searchUsers("   ", null, null, pageable);

            verify(usersRepository).findAll(any(Specification.class), eq(pageable));
        }

        /**
         * Test: Search users skips blank role.
         */
        @Test
        @Order(57)
        @DisplayName("Search users skips blank role")
        @SuppressWarnings("unchecked")
        void shouldSkipBlankRoleInSearch() {
            Pageable pageable = Pageable.unpaged();
            Page<Users> page = new PageImpl<>(List.of());

            when(usersRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

            userService.searchUsers(null, "  ", null, pageable);

            verify(usersRepository).findAll(any(Specification.class), eq(pageable));
        }

        /**
         * Test: Should throw exception if manager not found during complete
         * invite.
         */
        @Test
        @Order(58)
        @DisplayName("Complete invite fails if manager not found")
        void shouldThrowIfManagerNotFoundOnCompleteInvite() {
            String token = "valid-token";
            InviteToken inviteToken = InviteToken.builder()
                    .token(token)
                    .username("newuser")
                    .role("MANAGER")
                    .managerId(999L)
                    .expiryDate(LocalDateTime.now().plusHours(24))
                    .used(false)
                    .build();

            when(inviteTokenRepository.findByToken(token)).thenReturn(Optional.of(inviteToken));
            when(usersRepository.existsByUsername("newuser")).thenReturn(false);
            when(usersRepository.existsByManagerId(999L)).thenReturn(false);
            when(managersRepository.findById(999L)).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> userService.completeInvite(token, "password"));

            assertEquals("Manager not found", ex.getMessage());
            verify(usersRepository, never()).save(any(Users.class));
        }

        /**
         * Test: Should throw exception if role not found during complete
         * invite.
         */
        @Test
        @Order(59)
        @DisplayName("Complete invite fails if role not found")
        void shouldThrowIfRoleNotFoundOnCompleteInvite() {
            String token = "valid-token";
            Managers manager = Managers.builder().id(1L).email("mgr@company.com").build();

            InviteToken inviteToken = InviteToken.builder()
                    .token(token)
                    .username("newuser")
                    .role("INVALIDROLE")
                    .managerId(1L)
                    .expiryDate(LocalDateTime.now().plusHours(24))
                    .used(false)
                    .build();

            when(inviteTokenRepository.findByToken(token)).thenReturn(Optional.of(inviteToken));
            when(usersRepository.existsByUsername("newuser")).thenReturn(false);
            when(usersRepository.existsByManagerId(1L)).thenReturn(false);
            when(managersRepository.findById(1L)).thenReturn(Optional.of(manager));
            when(rolesRepository.findByRole("INVALIDROLE")).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> userService.completeInvite(token, "password"));

            assertEquals("Role not found: INVALIDROLE", ex.getMessage());
            verify(usersRepository, never()).save(any(Users.class));
        }
    }
}
