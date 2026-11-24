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
import com.donatodev.bcm_backend.util.AuthenticatedUserUtils;
import com.donatodev.bcm_backend.validation.PasswordPolicy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.donatodev.bcm_backend.auth.ReauthenticationService;
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
import com.donatodev.bcm_backend.util.TokenHash;

@Service
public class UserService {

	private static final String ERR_USER_ID = "Utente ID ";
	private static final String ERR_NOT_FOUND = " non trovato";
	private static final String ROLE_ADMIN = "ADMIN";

	private final UsersRepository usersRepository;
	private final UserMapper userMapper;
	private final PasswordEncoder passwordEncoder;
	private final ManagersRepository managersRepository;
	private final RolesRepository rolesRepository;
	private final InviteTokenRepository inviteTokenRepository;

	private final SessionRevocationService sessionRevocationService;
	private final AuditLogService auditLogService;
	private final PasswordStrengthService passwordStrength;
	private final ReauthenticationService reauthenticationService;

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
			SessionRevocationService sessionRevocationService,
			AuditLogService auditLogService,
			PasswordStrengthService passwordStrength,
			ReauthenticationService reauthenticationService
	) {
		this.passwordStrength = passwordStrength;
		this.reauthenticationService = reauthenticationService;
		this.usersRepository = usersRepository;
		this.userMapper = userMapper;
		this.passwordEncoder = passwordEncoder;
		this.managersRepository = managersRepository;
		this.rolesRepository = rolesRepository;
		this.inviteTokenRepository = inviteTokenRepository;
		this.sessionRevocationService = sessionRevocationService;
		this.auditLogService = auditLogService;
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
	@Transactional
	public Users registerUser(UserDTO dto) {
		UUID orgId = TenantContext.require();
		// An account with a password chosen by whoever creates it, an administrator's
		// included: the creator's password first (OWASP ASVS 7.5.1), as for setting
		// another user's password. Invitations need none: the link goes to the address.
		reauthenticationService.require();
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
		requireStrong(dto.password(), dto.username(), user.getOrganization(), user.getManager());
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
	@Transactional
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
	@Transactional
	public UserDTO updateUser(UUID id, UserDTO dto) {
		Users user = findUserInScope(id)
				.orElseThrow(() -> new UserNotFoundException(ERR_USER_ID + id + ERR_NOT_FOUND));
		if (dto.password() != null && !dto.password().isBlank()) {
			requireMaySetPasswordOf(user);
		}

		user.setUsername(dto.username());
		// No password in the request keeps the current one: encode(null) would
		// otherwise null the NOT NULL hash and fail the whole update.
		boolean passwordChanged = dto.password() != null && !dto.password().isBlank();
		if (passwordChanged) {
			requireStrong(dto.password(), dto.username(), user.getOrganization(), user.getManager());
			user.setPasswordHash(passwordEncoder.encode(dto.password()));
		}

		Managers newManager = findManagerInScope(dto.managerId())
				.orElseThrow(() -> new IllegalArgumentException("ID manager non trovato"));
		Roles newRole = rolesRepository.findById(dto.roleId())
				.orElseThrow(() -> new IllegalArgumentException("ID ruolo non trovato"));
		boolean newApprover = Boolean.TRUE.equals(dto.canApproveContracts());
		boolean managerChanged = !sameManager(user.getManager(), newManager);
		requireMayHandOver(user, managerChanged,
				!java.util.Objects.equals(roleName(user.getRole()), roleName(newRole))
						|| newApprover != user.isCanApproveContracts(),
				roleName(newRole));

		user.setManager(newManager);
		String roleBefore = roleName(user.getRole());
		user.setRole(newRole);
		user.setCanApproveContracts(newApprover);

		user = usersRepository.save(user);
		auditRoleChange(user, roleBefore);
		if (passwordChanged || (managerChanged && !isCaller(user))) {
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
	@Transactional
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
	 * Every account whose manager has the given email, in any organization:
	 * the email is unique only within one, so the same person may hold
	 * several accounts. Used by "forgot password" only.
	 *
	 * @param email the manager's email address
	 * @return the accounts at that address, possibly none
	 */
	public List<Users> findAllByEmail(String email) {
		return usersRepository.findAllByManagerEmailIgnoreCase(email);
	}

	/**
	 * Updates the password for the given user.
	 *
	 * @param user        the user entity whose password is to be updated
	 * @param newPassword the new password (plain text, will be encoded)
	 */
	@Transactional
	public void updatePassword(Users user, String newPassword) {
		requireStrong(newPassword, user.getUsername(), user.getOrganization(), user.getManager());
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
	@Transactional
	public void resetPassword(Users user, String newPassword) {
		user.setFailedLoginAttempts(0);
		user.setLockedUntil(null);
		updatePassword(user, newPassword);
		auditLogService.saveAboutUser("PASSWORD_RESET", user, "via emailed link");
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
		auditLogService.saveAboutUser("PASSWORD_CHANGED", user, null);
	}

	@Transactional
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
				// Only the hash is stored (OWASP API2); the link carries the token.
				.token(TokenHash.sha256Hex(token))
				.username(username)
				.role("MANAGER")
				.managerId(manager.getId())
				.expiryDate(LocalDateTime.now(ZoneId.systemDefault()).plusHours(24))
				.used(false)
				.build();
		inviteTokenRepository.save(it);
		// The link itself is a credential: only who was invited is recorded.
		auditLogService.save("USER_INVITED", "Manager", manager.getId(),
				AuthenticatedUserUtils.getUsernameOrNull(), orgId, "username=" + username + " role=MANAGER");
		return frontendBaseUrl + "/complete-invite?token=" + token;
	}

	@Transactional
	public void completeInvite(String token, String rawPassword) {
		InviteToken it = inviteTokenRepository.findByToken(TokenHash.sha256Hex(token))
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
		requireStrong(rawPassword, it.getUsername(), manager.getOrganization(), manager);
		user.setPasswordHash(passwordEncoder.encode(rawPassword));
		user.setManager(manager);
		user.setRole(role);
		user.setVerified(true);
		user.setOrganization(manager.getOrganization());
		usersRepository.save(user);

		it.setUsed(true);
		inviteTokenRepository.save(it);
		auditLogService.saveAboutUser("INVITE_ACCEPTED", user, "role=" + it.getRole());
	}

	@Transactional
	public Users updateUserPartial(UUID id, String username, String role, UUID managerId, String rawPassword) {
		Users user = findUserInScope(id)
				.orElseThrow(() -> new UserNotFoundException(ERR_USER_ID + id + ERR_NOT_FOUND));
		if (rawPassword != null && !rawPassword.isBlank()) {
			requireMaySetPasswordOf(user);
		}

		if (username != null && !username.isBlank()) {
			if (!username.equals(user.getUsername()) && usersRepository.existsByUsername(username)) {
				throw new IllegalArgumentException("Username già esistente.");
			}
			user.setUsername(username);
		}

		Roles newRole = null;
		if (role != null && !role.isBlank()) {
			newRole = rolesRepository.findByRole(role)
					.orElseThrow(() -> new IllegalArgumentException("Ruolo non trovato: " + role));
		}
		Managers newManager = null;
		if (managerId != null) {
			newManager = findManagerInScope(managerId)
					.orElseThrow(() -> new IllegalArgumentException("ID manager non trovato"));
		}
		boolean managerChanged = newManager != null && !sameManager(user.getManager(), newManager);
		boolean roleChanged = newRole != null && !java.util.Objects.equals(roleName(user.getRole()), roleName(newRole));
		requireMayHandOver(user, managerChanged, roleChanged,
				roleChanged ? roleName(newRole) : roleName(user.getRole()));

		String roleBefore = roleName(user.getRole());
		if (newRole != null) {
			user.setRole(newRole);
		}
		if (newManager != null) {
			user.setManager(newManager);
		}

		boolean passwordChanged = rawPassword != null && !rawPassword.isBlank();
		if (passwordChanged) {
			requireStrong(rawPassword, user.getUsername(), user.getOrganization(), user.getManager());
			user.setPasswordHash(passwordEncoder.encode(rawPassword));
		}

		Users saved = usersRepository.save(user);
		auditRoleChange(saved, roleBefore);
		if (passwordChanged || (managerChanged && !isCaller(saved))) {
			sessionRevocationService.credentialsChanged(saved);
		}
		return saved;
	}

	/**
	 * The changes that hand an account over, short of its password (security
	 * audit 2026-11-24): the manager it is linked to is where its password
	 * resets, sign-in alerts and deletion codes are mailed, so relinking it to a
	 * manager with another address lets "Password dimenticata" deliver it there;
	 * its role and approval right are what it may do. Each needs the caller's
	 * password again (OWASP ASVS 7.5.1), as changing an email does. Relinking
	 * another administrator is refused outright, as setting its password is
	 * (requireMaySetPasswordOf); the last administrator keeps the role, or
	 * nobody could manage the organization any more. Checked on the account as
	 * it is now, before any field of the request is applied.
	 */
	private void requireMayHandOver(Users target, boolean managerChanged, boolean privilegeChanged, String roleAfter) {
		if (!managerChanged && !privilegeChanged) {
			return;
		}
		boolean targetIsAdmin = ROLE_ADMIN.equals(roleName(target.getRole()));
		if (managerChanged && targetIsAdmin && !isCaller(target)) {
			throw new AccessDeniedException(
					"non puoi cambiare il responsabile collegato a un altro amministratore: è dove arrivano i suoi link di recupero");
		}
		reauthenticationService.require();
		if (targetIsAdmin && !ROLE_ADMIN.equals(roleAfter) && target.getOrganization() != null
				&& usersRepository.findByOrganizationIdAndRoleRole(target.getOrganization().getId(), ROLE_ADMIN).size() <= 1) {
			throw new IllegalArgumentException("Non puoi togliere il ruolo all'ultimo amministratore dell'organizzazione");
		}
	}

	private static boolean sameManager(Managers current, Managers next) {
		UUID currentId = current == null ? null : current.getId();
		UUID nextId = next == null ? null : next.getId();
		return java.util.Objects.equals(currentId, nextId);
	}

	private static boolean isCaller(Users user) {
		return java.util.Objects.equals(user.getUsername(), AuthenticatedUserUtils.getUsernameOrNull());
	}

	/**
	 * A change of role is recorded by name, from and to (OWASP Logging Cheat
	 * Sheet: changes of privilege): the generic UPDATE row would not say that a
	 * manager became an administrator. The actor is whoever is signed in; the
	 * row shares the update's transaction, so a change rolled back leaves none.
	 */
	private void auditRoleChange(Users user, String roleBefore) {
		String roleAfter = roleName(user.getRole());
		if (roleBefore == null ? roleAfter == null : roleBefore.equals(roleAfter)) {
			return;
		}
		UUID orgId = user.getOrganization() == null ? null : user.getOrganization().getId();
		auditLogService.save("ROLE_CHANGED", "User", user.getId(), AuthenticatedUserUtils.getUsernameOrNull(), orgId,
				"user=" + user.getUsername() + " role " + roleBefore + " -> " + roleAfter);
	}

	/**
	 * Every password set for an account: the length rule, then not common, not
	 * built on the account's or its organization's words, not leaked
	 * (PasswordStrengthService, OWASP ASVS 6.2).
	 */
	private void requireStrong(String password, String username, Organization organization, Managers manager) {
		PasswordPolicy.require(password);
		passwordStrength.require(password, username,
				organization == null ? null : organization.getName(),
				manager == null ? null : manager.getFirstName(),
				manager == null ? null : manager.getLastName(),
				manager == null ? null : manager.getEmail());
	}

	private static String roleName(Roles role) {
		return role == null ? null : role.getRole();
	}

	/**
	 * An administrator may set a manager's password (account recovery) but
	 * not another administrator's: that would let one admin sign in as
	 * another and act under their name, a horizontal escalation between
	 * equals (OWASP Authorization Cheat Sheet). The other admin recovers
	 * their own account through "Password dimenticata". Checked on the role
	 * the account has now, before any field of the request is applied.
	 */
	private void requireMaySetPasswordOf(Users target) {
		boolean targetIsAdmin = target.getRole() != null && ROLE_ADMIN.equals(target.getRole().getRole());
		if (targetIsAdmin && !target.getUsername().equals(AuthenticatedUserUtils.getUsernameOrNull())) {
			throw new AccessDeniedException(
					"non puoi impostare la password di un altro amministratore: può reimpostarla da \"Password dimenticata\"");
		}
		// Another account's password: the password of whoever sets it, again
		// first (OWASP ASVS 7.5.1). A stolen session must not hand over accounts.
		if (!java.util.Objects.equals(target.getUsername(), AuthenticatedUserUtils.getUsernameOrNull())) {
			reauthenticationService.require();
		}
	}

	public Users getEntityById(UUID id) {
		return findUserInScope(id)
				.orElseThrow(() -> new UserNotFoundException(ERR_USER_ID + id + ERR_NOT_FOUND));
	}

}