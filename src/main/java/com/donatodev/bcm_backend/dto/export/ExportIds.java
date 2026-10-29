/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto.export;

import java.util.function.Function;

/**
 * Export files reference each other by id, like database tables, instead of
 * nesting whole objects: contracts.json holds {@code managerId}, which matches
 * an {@code id} in managers.json.
 */
final class ExportIds {

    private ExportIds() {
    }

    /** The id of an optional reference, or null when the reference is unset. */
    static <T> Long idOf(T entity, Function<T, Long> id) {
        return entity != null ? id.apply(entity) : null;
    }
}
