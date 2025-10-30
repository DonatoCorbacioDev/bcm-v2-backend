package com.donatodev.bcm_backend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InviteToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String token;

    @Column(nullable = false)
    private LocalDateTime expiryDate;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private Long managerId;

    @Builder.Default
    @Column(nullable = false)
    private boolean used = false;
}