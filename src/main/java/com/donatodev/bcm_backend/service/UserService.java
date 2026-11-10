/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import com.donatodev.bcm_backend.util.AuthenticatedUserUtils;
import com.donatodev.bcm_backend.validation.PasswordPolicy;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.UserDTO;
import com.donatodev.bcm_backend.dto.UserProfileDTO;
import com.donatodev.bcm_backend.entity.InviteToken;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
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

	private static final String ERR_USER_ID = "Utente ID ";
	private static final String ERR_NOT_FOUND = " non trovato";

	private final UsersRepository usersRepository;
	private final UserMapper userMapper;
	private final PasswordEncoder passwordEncoder;
	private final ManagersRepository managersRepository;
	private final RolesRepository rolesRepository;
	private final InviteTokenRepository inviteTokenRepository;

	private final PasswordResetTokenService passwordResetTokenService;
	private final IEmailService emailService;
	private final SessionRevocationService sessionRevocationService;

	@Value("${app.frontend-base-url:http://localhost:3000}")
	private String frontendBaseUrl;

	// Same setting as PublicDemoGuard: tells the profile page to show the
	// password and two-factor cards as disabled for the public demo login.
	@Value("${bcm.demo.locked-username:}")
	private String demoLockedUsername;

	public UserService(
			UsersRepository usersRepository,
			UserMapper userMapper,
			PasswordEncoder passwordEncoder,
			ManagersRepository managersRepository,
			RolesRepository rolesRepository,
			InviteTokenRepository inviteTokenRepository,
			PasswordResetTokenService passwordResetTokenService,
			IEmailService emailService,
			SessionRevocationService sessionRevocationService
	) {
		this.usersRepository = usersRepository;
		this.userMapper = userMapper;
		this.passwordEncoder = passwordEncoder;
		this.managersRepository = managersRepository;
		this.rolesRepository = rolesRepository;
		this.inviteTokenRepository = inviteTokenRepository;
		this.passwordResetTokenService = passwordResetTokenService; // ✅
		this.emailService = emailService; // ✅
		this.sessionRevocationService = sessionRevocationService;
	}

	/**
	 * Retrieves all users accessible to the current tenant. Falls back to all
	 * users when no {@link TenantContext} is set.
	 *
	 * @return list of {@link UserDTO}
	 */
	public List<UserDTO> getAllUsers() {
		UUID orgId = TenantContext.require();
		List<Users> users = usersRepository.findAllByOrganizationId(orgId);
		return users.stream()
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
	public UserDTO getUserById(UUID id) {
		return findUserInScope(id)
				.map(userMapper::toDTO)
				.orElseThrow(() -> new UserNotFoundException(ERR_USER_ID + id + ERR_NOT_FOUND));
	}

	/**
	 * Finds a user by ID, scoped to the current tenant when
	 * {@link TenantContext} carries an organization ID.
	 */
	private Optional<Users> findUserInScope(UUID id) {
		UUID orgId = TenantContext.require();
		return usersRepository.findByIdAndOrganizationId(id, orgId);
	}

	/**
	 * Finds a manager by ID, scoped to the current tenant when
	 * {@link TenantContext} carries an organization ID.
	 */
	private Optional<Managers> findManagerInScope(UUID managerId) {
		UUID orgId = TenantContext.require();
		return managersRepository.findByIdAndOrganizationId(managerId, orgId);
	}

	/**
	 * Registers a new user with encoded password.
	 *
	 * @param dto the user data transfer object
	 * @return the registered {@link Users} entity
	 * @throws IllegalArgumentException if username or manager is already in use,
	 * or if the manager belongs to a different tenant than the caller
	 */
	public Users registerUser(UserDTO dto) {
		UUID orgId = TenantContext.require();
		if (usersRepository.existsByUsername(dto.username())) {
			throw new IllegalArgumentException("Username già esistente.");
		}

		if (dto.managerId() != null && usersRepository.existsByManagerId(dto.managerId())) {
			throw new IllegalArgumentException("Questo manager è già associato a un altro utente.");
		}

		Users user = userMapper.toEntity(dto);
		// Client IDs must never turn account creation into an update of an existing user.
		user.setId(null);
		// The DTO allows no password (an update keeps the current one); a new user needs one.
		PasswordPolicy.require(dto.password());
		user.setPasswordHash(passwordEncoder.encode(dto.password()));
		if (user.getManager() != null) {
			// userMapper.toEntity() resolves the manager by ID without tenant
			// scoping, so an admin could otherwise attach a manager - and thus
			// the new user - to a different organization. Reject it here.
			UUID managerOrgId = user.getManager().getOrganization() != null
					? user.getManager().getOrganization().getId() : null;
			if (!orgId.equals(managerOrgId)) {
				throw new IllegalArgumentException("ID manager non trovato");
			}
			user.setOrganization(user.getManager().getOrganization());
		} else {
			Organization org = new Organization();
			org.setId(orgId);
			user.setOrganization(org);
		}
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
	public UserDTO updateUser(UUID id, UserDTO dto) {
		Users user = findUserInScope(id)
				.orElseThrow(() -> new UserNotFoundException(ERR_USER_ID + id + ERR_NOT_FOUND));

		user.setUsername(dto.username());
		// No password in the request keeps the current one: encode(null) would
		// otherwise null the NOT NULL hash and fail the whole update.
		boolean passwordChanged = dto.password() != null && !dto.password().isBlank();
		if (passwordChanged) {
			PasswordPolicy.require(dto.password());
			user.setPasswordHash(passwordEncoder.encode(dto.password()));
		}

		user.setManager(findManagerInScope(dto.managerId())
				.orElseThrow(() -> new IllegalArgumentException("ID manager non trovato")));

		user.setRole(rolesRepository.findById(dto.roleId())
				.orElseThrow(() -> new IllegalArgumentException("ID ruolo non trovato")));

		user.setCanApproveContracts(Boolean.TRUE.equals(dto.canApproveContracts()));

		user = usersRepository.save(user);
		if (passwordChanged) {
			sessionRevocationService.credentialsChanged(user);
		}
		return userMapper.toDTO(user);
	}

	/**
	 * Deletes a user by their ID.
	 *
	 * @param id the ID of the user to delete
	 * @throws UserNotFoundException if the user is not found
	 */
	public void deleteUser(UUID id) {
		Users user = findUserInScope(id)
				.orElseThrow(() -> new UserNotFoundException(ERR_USER_ID + id + ERR_NOT_FOUND));
		usersRepository.delete(user);
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
		return usersRepository.findByManagerEmailIgnoreCase(email);
	}

	/**
	 * Updates the password for the given user.
	 *
	 * @param user        the user entity whose password is to be updated
	 * @param newPassword the new password (plain text, will be encoded)
	 */
	public void updatePassword(Users user, String newPassword) {
		PasswordPolicy.require(newPassword);
		user.setPasswordHash(passwordEncoder.encode(newPassword));
		usersRepository.save(user);
		sessionRevocationService.credentialsChanged(user);
	}

	/**
	 * Sets the password chosen through an emailed reset link. Proving access to
	 * the mailbox also lifts a login lock: the forgotten password is often why
	 * the account got locked.
	 *
	 * @param user        the user the reset link belongs to
	 * @param newPassword the new password (plain text, will be encoded)
	 */
	public void resetPassword(Users user, String newPassword) {
		user.setFailedLoginAttempts(0);
		user.setLockedUntil(null);
		updatePassword(user, newPassword);
	}

	/**
	 * Retrieves the profile information of the currently authenticated user.
	 *
	 * @return a {@link UserProfileDTO} containing profile information
	 * @throws UsernameNotFoundException if the authenticated user cannot be found
	 */
	@Transactional(readOnly = true)
	public UserProfileDTO getCurrentUserProfile() {
		String username = AuthenticatedUserUtils.getUsernameOrThrow();
		Users user = usersRepository.findByUsername(username)
				.orElseThrow(() -> new UsernameNotFoundException("Utente non trovato: " + username));

		Organization organization = user.getOrganization();

		return new UserProfileDTO(
				user.getId(),
				user.getUsername(),
				user.getRole().getRole(),
				user.isVerified(),
				user.isCanApproveContracts(),
				organization != null ? organization.getName() : null,
				user.getManager() != null ? user.getManager().getFirstName() : null,
				demoLockedUsername != null && !demoLockedUsername.isBlank()
						&& demoLockedUsername.trim().equals(user.getUsername())
		);
	}

	/**
	 * Changes the signed-in user's own password after checking the current one.
	 *
	 * @throws IllegalArgumentException if the current password is wrong
	 */
	@Transactional
	public void changeOwnPassword(String currentPassword, String newPassword) {
		String username = AuthenticatedUserUtils.getUsernameOrThrow();
		Users user = usersRepository.findByUsername(username)
				.orElseThrow(() -> new UsernameNotFoundException("Utente non trovato: " + username));
		if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
			throw new IllegalArgumentException("La password attuale non è corretta");
		}
		updatePassword(user, newPassword);
	}

	public String inviteUser(String username, String role, UUID managerId) {

		if (usersRepository.existsByUsername(username)) {
			throw new IllegalArgumentException("Username già esistente.");
		}

		if (!"MANAGER".equals(Normalizer.normalize(role, Normalizer.Form.NFC).toUpperCase(Locale.ROOT))) {
			throw new IllegalArgumentException("Solo il ruolo MANAGER è consentito tramite invito.");
		}
		// Scoped to the inviting admin's organization: completeInvite() puts
		// the new user in the manager's organization, so another tenant's
		// manager id would let an admin plant a user in that tenant.
		UUID orgId = TenantContext.require();
		Managers manager = managersRepository.findByIdAndOrganizationId(managerId, orgId)
				.orElseThrow(() -> new IllegalArgumentException("Manager non trovato"));

		String token = UUID.randomUUID().toString();

		InviteToken it = InviteToken.builder()
				.token(token)
				.username(username)
				.role("MANAGER")
				.managerId(manager.getId())
				.expiryDate(LocalDateTime.now(ZoneId.systemDefault()).plusHours(24))
				.used(false)
				.build();
		inviteTokenRepository.save(it);
		return frontendBaseUrl + "/complete-invite?token=" + token;
	}

	public void completeInvite(String token, String rawPassword) {
		InviteToken it = inviteTokenRepository.findByToken(token)
			.orElseThrow(() -> new IllegalArgumentException("Token di invito non valido"));

		if (it.isUsed() || it.getExpiryDate().isBefore(LocalDateTime.now(ZoneId.systemDefault()))) {
			throw new IllegalArgumentException("Token di invito già usato o scaduto");
		}

		if (usersRepository.existsByUsername(it.getUsername())) {
			throw new IllegalArgumentException("Utente già esistente.");
		}

		if (usersRepository.existsByManagerId(it.getManagerId())) {
			throw new IllegalArgumentException("Questo manager è già associato a un altro utente.");
		}

		Managers manager = managersRepository.findById(it.getManagerId())
			.orElseThrow(() -> new IllegalArgumentException("Manager non trovato"));
		Roles role = rolesRepository.findByRole(it.getRole())
			.orElseThrow(() -> new IllegalArgumentException("Ruolo non trovato: " + it.getRole()));

		Users user = new Users();
		user.setUsername(it.getUsername());
		PasswordPolicy.require(rawPassword);
		user.setPasswordHash(passwordEncoder.encode(rawPassword));
		user.setManager(manager);
		user.setRole(role);
		user.setVerified(true);
		user.setOrganization(manager.getOrganization());
		usersRepository.save(user);

		it.setUsed(true);
		inviteTokenRepository.save(it);
	}

	@Transactional
	public Users updateUserPartial(UUID id, String username, String role, UUID managerId, String rawPassword) {
		Users user = findUserInScope(id)
				.orElseThrow(() -> new UserNotFoundException(ERR_USER_ID + id + ERR_NOT_FOUND));

		if (username != null && !username.isBlank()) {
			if (!username.equals(user.getUsername()) && usersRepository.existsByUsername(username)) {
				throw new IllegalArgumentException("Username già esistente.");
			}
			user.setUsername(username);
		}

		if (role != null && !role.isBlank()) {
			Roles r = rolesRepository.findByRole(role)
					.orElseThrow(() -> new IllegalArgumentException("Ruolo non trovato: " + role));
			user.setRole(r);
		}

		if (managerId != null) {
			Managers m = findManagerInScope(managerId)
					.orElseThrow(() -> new IllegalArgumentException("ID manager non trovato"));
			user.setManager(m);
		}

		boolean passwordChanged = rawPassword != null && !rawPassword.isBlank();
		if (passwordChanged) {
			PasswordPolicy.require(rawPassword);
			user.setPasswordHash(passwordEncoder.encode(rawPassword));
		}

		Users saved = usersRepository.save(user);
		if (passwordChanged) {
			sessionRevocationService.credentialsChanged(saved);
		}
		return saved;
	}

	public Users getEntityById(UUID id) {
		return findUserInScope(id)
				.orElseThrow(() -> new UserNotFoundException(ERR_USER_ID + id + ERR_NOT_FOUND));
	}

	public void sendResetLink(UUID userId) {
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

		UUID orgId = TenantContext.require();
		spec = spec.and((root, query, cb) ->
			cb.equal(root.get("organization").get("id"), orgId)
		);

		if (q != null && !q.isBlank()) {
			spec = spec.and((root, query, cb) ->
				cb.like(cb.lower(root.get("username")), "%" + q.toLowerCase(Locale.ROOT) + "%")
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