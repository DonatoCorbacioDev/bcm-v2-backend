package com.donatodev.bcm_backend.service;

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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.dto.UserDTO;
import com.donatodev.bcm_backend.dto.UserProfileDTO;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.UserNotFoundException;
import com.donatodev.bcm_backend.mapper.UserMapper;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

/**
 * Unit tests for {@link UserService}.
 * <p>
 * This class verifies core user management logic, including:
 * <ul>
 *   <li>Retrieving all users and individual users by ID</li>
 *   <li>Creating, updating, and deleting users</li>
 *   <li>Fetching the current authenticated user's profile</li>
 *   <li>Updating user passwords and validating uniqueness constraints</li>
 *   <li>Finding users by manager email</li>
 * </ul>
 * <p>
 * Uses {@code Mockito} for mocking dependencies and simulates security context
 * via {@link SecurityContextHolder} for role-based operations.
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class UserServiceTest {

    @Mock private 
    UsersRepository usersRepository;
    
    @Mock private 
    UserMapper userMapper;
    
    @Mock 
    private PasswordEncoder passwordEncoder;
    
    @Mock 
    private ManagersRepository managersRepository;
    
    @Mock
    private RolesRepository rolesRepository;
    
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
            UserDTO dto = new UserDTO(1L, "admin", "pass", 1L, 1L);

            when(usersRepository.findAll()).thenReturn(List.of(user));
            when(userMapper.toDTO(user)).thenReturn(dto);

            List<UserDTO> result = userService.getAllUsers();

            assertEquals(1, result.size());
            assertEquals("admin", result.get(0).username());
        }

        /**
         * Test: Retrieve a single user by ID and map to DTO.
         * Verifies username is correctly returned.
         */
        @Test
        @Order(2)
        @DisplayName("Get user by ID")
        void shouldGetUserById() {
            Users user = Users.builder().id(1L).username("admin").build();
            UserDTO dto = new UserDTO(1L, "admin", "pass", 1L, 1L);

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
            UserNotFoundException ex =
                assertThrows(UserNotFoundException.class, () -> userService.getUserById(999L));
            assertEquals("User ID 999 not found", ex.getMessage());
        }

        /**
         * Test: Successfully create a new user.
         * Ensures username uniqueness, password encoding, and correct DTO result.
         */
        @Test
        @Order(4)
        @DisplayName("Create user successfully")
        void shouldCreateUser() {
            UserDTO dto = new UserDTO(null, "user1", "plainpass", 1L, 1L);
            Users user = Users.builder().username("user1").build();
            Users saved = Users.builder().id(1L).username("user1").build();
            UserDTO savedDTO = new UserDTO(1L, "user1", null, 1L, 1L);

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
            UserDTO dto = new UserDTO(1L, "updatedUser", "newpass", 2L, 3L);
            UserDTO resultDTO = new UserDTO(1L, "updatedUser", null, 2L, 3L);

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
         * Test: Delete a user by ID.
         * Verifies repository delete method is called.
         */
        @Test
        @Order(6)
        @DisplayName("Delete user by ID")
        void shouldDeleteUser() {
            userService.deleteUser(1L);
            verify(usersRepository).deleteById(1L);
        }

        /**
         * Test: Retrieve the currently authenticated user's profile.
         * Simulates security context and validates role and verification flag.
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
         * Test: Find user by associated manager email address.
         * Verifies correct filtering among all users.
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
         * Test: Update a user's password and persist the change.
         * Verifies password encoding and repository save.
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
         * Test: Throw exception when trying to register with an existing username.
         */
        @Test
        @Order(10)
        @DisplayName("Register user fails if username exists")
        void shouldThrowIfUsernameExists() {
            UserDTO dto = new UserDTO(null, "existingUser", "pass", null, null);
            when(usersRepository.existsByUsername("existingUser")).thenReturn(true);

            IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> userService.createUser(dto));
            assertEquals("Username already exists.", ex.getMessage());
        }
        
        /**
         * Test: Should throw exception if manager ID is already assigned to another user during registration.
         */
        @Test
        @Order(11)
        @DisplayName("Register user fails if manager already assigned")
        void shouldThrowIfManagerAlreadyAssigned() {
            UserDTO dto = new UserDTO(null, "newuser", "pass", 1L, null);
            when(usersRepository.existsByUsername("newuser")).thenReturn(false);
            when(usersRepository.existsByManagerId(1L)).thenReturn(true);

            IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> userService.createUser(dto));
            assertEquals("This manager is already associated with another user.", ex.getMessage());
        }

        /**
         * Test: Should throw exception if manager is not found during user update.
         */
        @Test
        @Order(12)
        @DisplayName("Update user fails if manager not found")
        void shouldThrowIfManagerNotFound() {
            UserDTO dto = new UserDTO(1L, "user", "pass", 10L, 1L);
            Users user = Users.builder().id(1L).build();

            when(usersRepository.findById(1L)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode("pass")).thenReturn("encoded");
            when(managersRepository.findById(10L)).thenReturn(Optional.empty());

            IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> userService.updateUser(1L, dto));
            assertEquals("Manager ID not found", ex.getMessage());
        }
        
        /**
         * Test: Should throw exception if role is not found during user update.
         */
        @Test
        @Order(13)
        @DisplayName("Update user fails if role not found")
        void shouldThrowIfRoleNotFound() {
            UserDTO dto = new UserDTO(1L, "user", "pass", 1L, 20L);
            Users user = Users.builder().id(1L).build();
            Managers manager = Managers.builder().id(1L).build();

            when(usersRepository.findById(1L)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode("pass")).thenReturn("encoded");
            when(managersRepository.findById(1L)).thenReturn(Optional.of(manager));
            when(rolesRepository.findById(20L)).thenReturn(Optional.empty());

            IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> userService.updateUser(1L, dto));
            assertEquals("Role ID not found", ex.getMessage());
        }

        /**
         * Test: Should throw exception if current authenticated user is not found in the database.
         */
        @Test
        @Order(14)
        @DisplayName("Get current user profile fails if user not found")
        void shouldThrowIfCurrentUserNotFound() {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("ghost", null)
            );

            when(usersRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            UsernameNotFoundException ex =
                assertThrows(UsernameNotFoundException.class, () -> userService.getCurrentUserProfile());
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
         * Test: Successfully register user when manager ID is provided and not yet assigned.
         */
        @Test
        @Order(17)
        @DisplayName("Register user passes if managerId is not null and not assigned")
        void shouldRegisterUserIfManagerIdNotAssigned() {
            UserDTO dto = new UserDTO(null, "newUser", "password", 1L, 2L);
            Users user = Users.builder().username("newUser").build();
            Users saved = Users.builder().id(1L).username("newUser").build();
            UserDTO savedDTO = new UserDTO(1L, "newUser", null, 1L, 2L);

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
         * Test: Should skip user in findByEmail() if user has no manager assigned.
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
            UserDTO dto = new UserDTO(null, "userNoManager", "pass", null, 2L);
            Users user = Users.builder().username("userNoManager").build();
            Users saved = Users.builder().id(1L).username("userNoManager").build();
            UserDTO savedDTO = new UserDTO(1L, "userNoManager", null, null, 2L);

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
         * Test: Should throw UserNotFoundException with correct message when user ID does not exist.
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
         * Test: Should throw UserNotFoundException with correct message during update when user ID is not found.
         */
        @Test
        @Order(22)
        @DisplayName("Update user throws with correct UserNotFoundException message")
        void shouldThrowUserNotFoundWithCorrectMessageInUpdate() {
            long userId = 999L;
            UserDTO dto = new UserDTO(userId, "updatedUser", "pass", 1L, 1L);

            when(usersRepository.findById(userId)).thenReturn(Optional.empty());

            UserNotFoundException exception = assertThrows(UserNotFoundException.class, () -> userService.updateUser(userId, dto));

            assertEquals("User ID 999 not found", exception.getMessage());
        }
    }
}