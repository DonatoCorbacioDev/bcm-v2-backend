package com.donatodev.bcm_backend.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.donatodev.bcm_backend.entity.InviteToken;

public interface InviteTokenRepository extends JpaRepository<InviteToken, Long> {
    Optional<InviteToken> findByToken(String token);
}
