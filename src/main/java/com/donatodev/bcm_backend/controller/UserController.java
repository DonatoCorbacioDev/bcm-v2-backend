package com.donatodev.bcm_backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.donatodev.bcm_backend.dto.InviteUserRequest;
import com.donatodev.bcm_backend.dto.UpdateUserRequest;
import com.donatodev.bcm_backend.dto.UserDTO;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.mapper.UserMapper;
import com.donatodev.bcm_backend.service.UserService;

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

	public UserController(UserService userService, UserMapper userMapper) {
        this.userService = userService;
        this.userMapper = userMapper;
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
    public ResponseEntity<UserDTO> getUserById(@PathVariable Long id) {
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
    public ResponseEntity<UserDTO> createUser(@RequestBody UserDTO userDTO) {
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
    public ResponseEntity<UserDTO> updateUser(@PathVariable Long id, @RequestBody UserDTO userDTO) {
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
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
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
    public ResponseEntity<UserDTO> patchUser(@PathVariable Long id, @RequestBody @Valid UpdateUserRequest req) {
        Users updated = userService.updateUserPartial(
                id,
                req.getUsername(),
                req.getRole(),
                req.getManagerId(),
                req.getPassword() 
        );
        return ResponseEntity.ok(userMapper.toDTO(updated));
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/force-reset")
    public ResponseEntity<Void> forceReset(@PathVariable Long id) {
        userService.sendResetLink(id);
        return ResponseEntity.noContent().build();
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/search")
    public ResponseEntity<Page<UserDTO>> searchUsers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String role,       
            @RequestParam(required = false) Boolean verified,   
            Pageable pageable
    ) {
        Page<Users> page = userService.searchUsers(q, role, verified, pageable);
        Page<UserDTO> dto = page.map(userMapper::toDTO);
        return ResponseEntity.ok(dto);
    }

}
