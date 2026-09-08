/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.entity.TotpRecoveryCode;

@Repository
public interface TotpRecoveryCodeRepository extends JpaRepository<TotpRecoveryCode, Long> {

    List<TotpRecoveryCode> findByUserIdAndUsedAtIsNull(Long userId);

    void deleteByUserId(Long userId);
}
