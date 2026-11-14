package com.donatodev.bcm_backend.validation;

/**
 * Validation group for the constraints that apply when a record is created
 * or replaced (POST, PUT) but not to a partial update (PATCH), where a
 * missing field means "leave it as it is".
 */
public interface OnCreate {
}
