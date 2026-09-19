package com.booknest.auth.domain;

/** Authorization role carried inside the JWT and checked by consumers (Rails, this service). */
public enum Role {
    USER,
    ADMIN
}
