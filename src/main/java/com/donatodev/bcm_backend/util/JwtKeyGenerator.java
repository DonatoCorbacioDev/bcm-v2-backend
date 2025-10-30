package com.donatodev.bcm_backend.util;

import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jsonwebtoken.Jwts;

/**
 * Utility class for generating a secure JWT secret key
 * encoded in Base64 format, suitable for HMAC-SHA algorithms.
 * <p>
 * Updated for JJWT 0.12.6 API compatibility.
 * 
 * @author Donato Corbacio
 * @version 2.0
 * @since 1.0.0
 */
public final class JwtKeyGenerator {
	
	private static final Logger logger = LoggerFactory.getLogger(JwtKeyGenerator.class);

    // Private constructor to prevent instantiation
    private JwtKeyGenerator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Main method to generate and print a Base64-encoded secret key for JWT HMAC-SHA256.
     * <p>
     * Run this utility to obtain a secure secret key string
     * to use in your application configuration.
     *
     * @param args command line arguments (not used)
     */
    public static void main(String[] args) {
        // Updated for JJWT 0.12.6: Use Jwts.SIG.HS256.key().build()
        byte[] key = Jwts.SIG.HS256.key().build().getEncoded();
        String base64Key = Base64.getEncoder().encodeToString(key);
        logger.info("JWT Secret (HMAC-SHA256, base64):");
        logger.info(base64Key);
    }
}