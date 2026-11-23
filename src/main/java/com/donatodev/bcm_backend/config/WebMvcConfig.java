/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.donatodev.bcm_backend.security.CostlyOperationLimiter;
import com.donatodev.bcm_backend.security.PublicDemoGuard;
import com.donatodev.bcm_backend.security.WriteBudgetLimiter;

/** Registers the MVC interceptors. */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final PublicDemoGuard publicDemoGuard;
    private final CostlyOperationLimiter costlyOperationLimiter;
    private final WriteBudgetLimiter writeBudgetLimiter;

    public WebMvcConfig(PublicDemoGuard publicDemoGuard, CostlyOperationLimiter costlyOperationLimiter,
            WriteBudgetLimiter writeBudgetLimiter) {
        this.publicDemoGuard = publicDemoGuard;
        this.costlyOperationLimiter = costlyOperationLimiter;
        this.writeBudgetLimiter = writeBudgetLimiter;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(publicDemoGuard);
        registry.addInterceptor(costlyOperationLimiter);
        registry.addInterceptor(writeBudgetLimiter);
    }
}
