package com.donatodev.bcm_backend.dto;

/**
 * Data Transfer Object for Business Area.
 * <p>
 * Used to transfer data between client and server in REST API operations.
 *
 * @param id          the ID of the business area
 * @param name        the name of the business area
 * @param description a brief description of the business area
 */
public record BusinessAreaDTO(
        Long id,
        String name,
        String description
) {}
