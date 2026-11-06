/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.donatodev.bcm_backend.security.PublicDemoGuard;

/** Registers the MVC interceptors. */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final PublicDemoGuard publicDemoGuard;

    public WebMvcConfig(PublicDemoGuard publicDemoGuard) {
        this.publicDemoGuard = publicDemoGuard;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(publicDemoGuard);
    }
}
