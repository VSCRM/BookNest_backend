package com.booknest.auth.domain;

/** How a user's identity was established. */
public enum AuthProvider {
    /** Registered with email + password on this service. */
    LOCAL,
    /** Signed in via Google OAuth2 — has no local password. */
    GOOGLE
}
