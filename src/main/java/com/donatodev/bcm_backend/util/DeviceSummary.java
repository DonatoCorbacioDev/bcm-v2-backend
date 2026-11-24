/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.util;

/**
 * A User-Agent reduced to the kind of device a person would recognise:
 * "Chrome su Windows", "Safari su iPhone". Only the browser's family and the
 * operating system, never versions: they change with every update, and a
 * sign-in from the same laptop after one must not look like a new device
 * (NewDeviceMonitor). Unrecognised parts are named generically.
 */
public final class DeviceSummary {

    static final String UNKNOWN_BROWSER = "Browser sconosciuto";
    static final String UNKNOWN_SYSTEM = "sistema sconosciuto";

    private DeviceSummary() {
    }

    public static String of(String userAgent) {
        String ua = userAgent == null ? "" : userAgent;
        return browser(ua) + " su " + system(ua);
    }

    // Order matters: Edge and Opera say "Chrome" too, Chrome says "Safari".
    private static String browser(String ua) {
        if (ua.contains("Edg/") || ua.contains("Edge/")) {
            return "Edge";
        }
        if (ua.contains("OPR/") || ua.contains("Opera")) {
            return "Opera";
        }
        if (ua.contains("Firefox/") || ua.contains("FxiOS/")) {
            return "Firefox";
        }
        if (ua.contains("Chrome/") || ua.contains("CriOS/")) {
            return "Chrome";
        }
        if (ua.contains("Safari/")) {
            return "Safari";
        }
        return UNKNOWN_BROWSER;
    }

    // Order matters: Android says "Linux", iOS says "like Mac OS X".
    private static String system(String ua) {
        if (ua.contains("Android")) {
            return "Android";
        }
        if (ua.contains("iPhone")) {
            return "iPhone";
        }
        if (ua.contains("iPad")) {
            return "iPad";
        }
        if (ua.contains("Windows")) {
            return "Windows";
        }
        if (ua.contains("Mac OS X") || ua.contains("Macintosh")) {
            return "macOS";
        }
        if (ua.contains("CrOS")) {
            return "ChromeOS";
        }
        if (ua.contains("Linux")) {
            return "Linux";
        }
        return UNKNOWN_SYSTEM;
    }
}
