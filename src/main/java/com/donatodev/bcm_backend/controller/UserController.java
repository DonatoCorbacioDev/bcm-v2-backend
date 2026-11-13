/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.controller;

import java.util.UUID;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;

import com.donatodev.bcm_backend.dto.ChangeOwnPasswordRequest;
import com.donatodev.bcm_backend.dto.InviteUserRequest;
import com.donatodev.bcm_backend.dto.UpdateUserRequest;
import com.donatodev.bcm_backend.dto.UserDTO;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.mapper.UserMapper;
import com.donatodev.bcm_backend.service.UserService;
import com.donatodev.bcm_backend.security.DisabledInPublicDemo;
import com.donatodev.bcm_backend.auth.CurrentSessionRenewer;

import jakarta.validation.Valid;

/**
 * REST controller for managing users.
 * <p>
 * Provides endpoints to retrieve, create, update, and delete users.
 */
@RestController
@RequestMapping("/users")
public class UserController {

	private final UserService userService;
	private final UserMapper userMapper;
	private final CurrentSessionRenewer currentSessionRenewer;

	public UserController(UserService userService, UserMapper userMapper,
			CurrentSessionRenewer currentSessionRenewer) {
        this.userService = userService;
        this.userMapper = userMapper;
        this.currentSessionRenewer = currentSessionRenewer;
    }

    /**
     * Changes the signed-in user's own password, whatever their role (the
     * admin-only PATCH /users/{id} is for managing other people).
     */
    @PreAuthorize("isAuthenticated()")
    @DisabledInPublicDemo
    @PostMapping("/me/password")
    public ResponseEntity<Void> changeOwnPassword(@RequestBody @Valid ChangeOwnPasswordRequest request) {
        userService.changeOwnPassword(request.currentPassword(), request.newPassword());
        // Every other session ended with the change; this one goes on.
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, currentSessionRenewer.renewedRefreshCookie())
                .build();
    }

    /**
     * Retrieves all users.
     *
     * @return a list of {@link UserDTO}
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        List<UserDTO> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    /**
     * Retrieves a user by ID.
     *
     * @param id the ID of the user
     * @return the {@link UserDTO}
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getUserById(@PathVariable UUID id) {
        UserDTO user = userService.getUserById(id);
        return ResponseEntity.ok(user);
    }

    /**
     * Creates a new user.
     *
     * @param userDTO the user to create
     * @return the created {@link UserDTO}
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<UserDTO> createUser(@Valid @RequestBody UserDTO userDTO) {
        UserDTO newUser = userService.createUser(userDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(newUser);
    }

    /**
     * Updates an existing user.
     *
     * @param id      the ID of the user to update
     * @param userDTO the updated user data
     * @return the updated {@link UserDTO}
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<UserDTO> updateUser(@PathVariable UUID id, @Valid @RequestBody UserDTO userDTO) {
        UserDTO updatedUser = userService.updateUser(id, userDTO);
        return ResponseEntity.ok(updatedUser);
    }

    /**
     * Deletes a user by ID.
     *
     * @param id the ID of the user to delete
     * @return HTTP 204 No Content if deletion is successful
     */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/invite")
    public ResponseEntity<Map<String,String>> invite(@RequestBody @Valid InviteUserRequest req) {
        String link = userService.inviteUser(req.getUsername(), req.getRole(), req.getManagerId());
        return ResponseEntity.ok(Map.of("inviteLink", link));
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}")
    public ResponseEntity<UserDTO> patchUser(@PathVariable UUID id, @RequestBody @Valid UpdateUserRequest req) {
        Users updated = userService.updateUserPartial(
                id,
                req.getUsername(),
                req.getRole(),
                req.getManagerId(),
                req.getPassword() 
        );
        return ResponseEntity.ok(userMapper.toDTO(updated));
    }

}
