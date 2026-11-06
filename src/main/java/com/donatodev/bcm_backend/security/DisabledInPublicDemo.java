/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an endpoint the public demo account may not call: anything that would
 * lock other visitors out (password, two-factor) or break the demo story for
 * them until the nightly reset (paying its invoices, changing their bank details).
 * Enforced by {@link PublicDemoGuard}; a no-op unless
 * {@code bcm.demo.locked-username} is set.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DisabledInPublicDemo {
}
