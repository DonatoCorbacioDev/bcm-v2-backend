package com.donatodev.bcm_backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.donatodev.bcm_backend.dto.UserDTO;
import com.donatodev.bcm_backend.dto.UserProfileDTO;
import com.donatodev.bcm_backend.entity.InviteToken;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.UserNotFoundException;
import com.donatodev.bcm_backend.mapper.UserMapper;
import com.donatodev.bcm_backend.repository.InviteTokenRepository;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

@Service
public class UserService {

	private static final String ERR_USER_ID = "User ID ";
	private static final String ERR_NOT_FOUND = " not found";

	private final UsersRepository usersRepository;
	private final UserMapper userMapper;
	private final PasswordEncoder passwordEncoder;
	private final ManagersRepository managersRepository;
	private final RolesRepository rolesRepository;
	private final InviteTokenRepository inviteTokenRepository;

	private final PasswordResetTokenService passwordResetTokenService;
	private final IEmailService emailService;

	@Value("${app.frontend-base-url:http://localhost:3000}")
	private String frontendBaseUrl;

	public UserService(
			UsersRepository usersRepository,
			UserMapper userMapper,
			PasswordEncoder passwordEncoder,
			ManagersRepository managersRepository,
			RolesRepository rolesRepository,
			InviteTokenRepository inviteTokenRepository,
			PasswordResetTokenService passwordResetTokenService,
			IEmailService emailService
	) {
		this.usersRepository = usersRepository;
		this.userMapper = userMapper;
		this.passwordEncoder = passwordEncoder;
		this.managersRepository = managersRepository;
		this.rolesRepository = rolesRepository;
		this.inviteTokenRepository = inviteTokenRepository;
		this.passwordResetTokenService = passwordResetTokenService; // ✅
		this.emailService = emailService; // ✅
	}

	/**
	 * Retrieves all users.
	 *
	 * @return list of {@link UserDTO}
	 */
	public List<UserDTO> getAllUsers() {
		return usersRepository.findAll()
				.stream()
				.map(userMapper::toDTO)
				.toList();
	}

	/**
	 * Retrieves a user by their ID.
	 *
	 * @param id the user ID
	 * @return the corresponding {@link UserDTO}
	 * @throws UserNotFoundException if the user is not found
	 */
	public UserDTO getUserById(Long id) {
		return usersRepository.findById(id)
				.map(userMapper::toDTO)
				.orElseThrow(() -> new UserNotFoundException(ERR_USER_ID + id + ERR_NOT_FOUND));
	}

	/**
	 * Registers a new user with encoded password.
	 *
	 * @param dto the user data transfer object
	 * @return the registered {@link Users} entity
	 * @throws IllegalArgumentException if username or manager is already in use
	 */
	public Users registerUser(UserDTO dto) {
		if (usersRepository.existsByUsername(dto.username())) {
			throw new IllegalArgumentException("Username already exists.");
		}

		if (dto.managerId() != null && usersRepository.existsByManagerId(dto.managerId())) {
			throw new IllegalArgumentException("This manager is already associated with another user.");
		}

		Users user = userMapper.toEntity(dto);
		user.setPasswordHash(passwordEncoder.encode(dto.password()));
		return usersRepository.save(user);
	}

	/**
	 * Creates a new user.
	 *
	 * @param dto the user data transfer object
	 * @return the created {@link UserDTO}
	 */
	public UserDTO createUser(UserDTO dto) {
		Users user = registerUser(dto);
		return userMapper.toDTO(user);
	}

	/**
	 * Updates an existing user.
	 *
	 * @param id  the ID of the user to update
	 * @param dto the updated user data transfer object
	 * @return the updated {@link UserDTO}
	 * @throws UserNotFoundException if the user is not found
	 * @throws IllegalArgumentException if referenced manager or role is not found
	 */
	public UserDTO updateUser(Long id, UserDTO dto) {
		Users user = usersRepository.findById(id)
				.orElseThrow(() -> new UserNotFoundException(ERR_USER_ID + id + ERR_NOT_FOUND));

		user.setUsername(dto.username());
		user.setPasswordHash(passwordEncoder.encode(dto.password()));

		user.setManager(managersRepository.findById(dto.managerId())
				.orElseThrow(() -> new IllegalArgumentException("Manager ID not found")));

		user.setRole(rolesRepository.findById(dto.roleId())
				.orElseThrow(() -> new IllegalArgumentException("Role ID not found")));

		user = usersRepository.save(user);
		return userMapper.toDTO(user);
	}

	/**
	 * Deletes a user by their ID.
	 *
	 * @param id the ID of the user to delete
	 */
	public void deleteUser(Long id) {
		usersRepository.deleteById(id);
	}

	/**
	 * Saves the given user entity.
	 *
	 * @param user the user entity to save
	 */
	public void save(Users user) {
		usersRepository.save(user);
	}

	/**
	 * Finds a user by the email of their associated manager.
	 *
	 * @param email the manager's email address
	 * @return an {@link Optional} containing the found {@link Users} if any
	 */
	public Optional<Users> findByEmail(String email) {
		return usersRepository.findAll().stream()
				.filter(user ->
						user.getManager() != null &&
						user.getManager().getEmail() != null &&
						user.getManager().getEmail().equalsIgnoreCase(email)
				)
				.findFirst();
	}

	/**
	 * Updates the password for the given user.
	 *
	 * @param user        the user entity whose password is to be updated
	 * @param newPassword the new password (plain text, will be encoded)
	 */
	public void updatePassword(Users user, String newPassword) {
		user.setPasswordHash(passwordEncoder.encode(newPassword));
		usersRepository.save(user);
	}

	/**
	 * Retrieves the profile information of the currently authenticated user.
	 *
	 * @return a {@link UserProfileDTO} containing profile information
	 * @throws UsernameNotFoundException if the authenticated user cannot be found
	 */
	public UserProfileDTO getCurrentUserProfile() {
		String username = SecurityContextHolder.getContext().getAuthentication().getName();
		Users user = usersRepository.findByUsername(username)
				.orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

		return new UserProfileDTO(
				user.getId(),
				user.getUsername(),
				user.getRole().getRole(),
				user.isVerified()
		);
	}

	public String inviteUser(String username, String role, Long managerId) {

		if (usersRepository.existsByUsername(username)) {
			throw new IllegalArgumentException("Username already exists.");
		}

		if (!"MANAGER".equalsIgnoreCase(role)) {
			throw new IllegalArgumentException("Only MANAGER role allowed via invite.");
		}
		Managers manager = managersRepository.findById(managerId)
				.orElseThrow(() -> new IllegalArgumentException("Manager not found"));

		String token = UUID.randomUUID().toString();

		InviteToken it = InviteToken.builder()
				.token(token)
				.username(username)
				.role("MANAGER")
				.managerId(manager.getId())
				.expiryDate(LocalDateTime.now().plusHours(24))
				.used(false)
				.build();
		inviteTokenRepository.save(it);
		return frontendBaseUrl + "/complete-invite?token=" + token;
	}

	public void completeInvite(String token, String rawPassword) {
		InviteToken it = inviteTokenRepository.findByToken(token)
			.orElseThrow(() -> new IllegalArgumentException("Invalid invite token"));

		if (it.isUsed() || it.getExpiryDate().isBefore(LocalDateTime.now())) {
			throw new IllegalArgumentException("Invite token used or expired");
		}

		if (usersRepository.existsByUsername(it.getUsername())) {
			throw new IllegalArgumentException("User already exists.");
		}

		if (usersRepository.existsByManagerId(it.getManagerId())) {
			throw new IllegalArgumentException("This manager is already associated with another user.");
		}

		Managers manager = managersRepository.findById(it.getManagerId())
			.orElseThrow(() -> new IllegalArgumentException("Manager not found"));
		Roles role = rolesRepository.findByRole(it.getRole())
			.orElseThrow(() -> new IllegalArgumentException("Role not found: " + it.getRole()));

		Users user = new Users();
		user.setUsername(it.getUsername());
		user.setPasswordHash(passwordEncoder.encode(rawPassword));
		user.setManager(manager);
		user.setRole(role);
		user.setVerified(true);
		usersRepository.save(user);

		it.setUsed(true);
		inviteTokenRepository.save(it);
	}

	@Transactional
	public Users updateUserPartial(Long id, String username, String role, Long managerId, String rawPassword) {
		Users user = usersRepository.findById(id)
				.orElseThrow(() -> new UserNotFoundException(ERR_USER_ID + id + ERR_NOT_FOUND));

		if (username != null && !username.isBlank()) {
			if (!username.equals(user.getUsername()) && usersRepository.existsByUsername(username)) {
				throw new IllegalArgumentException("Username already exists.");
			}
			user.setUsername(username);
		}

		if (role != null && !role.isBlank()) {
			Roles r = rolesRepository.findByRole(role)
					.orElseThrow(() -> new IllegalArgumentException("Role not found: " + role));
			user.setRole(r);
		}

		if (managerId != null) {
			Managers m = managersRepository.findById(managerId)
					.orElseThrow(() -> new IllegalArgumentException("Manager ID not found"));
			user.setManager(m);
		}

		if (rawPassword != null && !rawPassword.isBlank()) {
			user.setPasswordHash(passwordEncoder.encode(rawPassword));
		}

		return usersRepository.save(user);
	}

	public Users getEntityById(Long id) {
		return usersRepository.findById(id)
				.orElseThrow(() -> new UserNotFoundException(ERR_USER_ID + id + ERR_NOT_FOUND));
	}

	public void sendResetLink(Long userId) {
		Users u = getEntityById(userId);
		var token = passwordResetTokenService.createToken(u);

		String resetLink = frontendBaseUrl + "/auth/reset-password?token=" + token.getToken();

		String recipient = (u.getManager() != null && u.getManager().getEmail() != null)
				? u.getManager().getEmail()
				: u.getUsername();

		emailService.sendResetPasswordEmail(recipient, resetLink);
	}

	public Page<Users> searchUsers(String q, String role, Boolean verified, Pageable pageable) {
		Specification<Users> spec = Specification.allOf();

		if (q != null && !q.isBlank()) {
			spec = spec.and((root, query, cb) ->
				cb.like(cb.lower(root.get("username")), "%" + q.toLowerCase() + "%")
			);
		}
		if (role != null && !role.isBlank()) {
			spec = spec.and((root, query, cb) ->
				cb.equal(root.join("role").get("role"), role)
			);
		}
		if (verified != null) {
			spec = spec.and((root, query, cb) ->
				cb.equal(root.get("verified"), verified)
			);
		}

		return usersRepository.findAll(spec, pageable);
	}
}