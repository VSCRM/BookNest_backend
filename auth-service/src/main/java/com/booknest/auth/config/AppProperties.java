package com.booknest.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Binds the {@code booknest.app.*} properties — cross-service URLs and deployment flags.
 *
 * @param frontendUrl Where to redirect the browser after a successful OAuth2 login.
 * @param corsAllowedOrigins Origins allowed to send credentialed cross-origin requests to this service.
 * @param cookieSecure Whether auth cookies are marked {@code Secure}. Must be {@code true} in
 *         any deployment served over HTTPS (i.e. everywhere except plain-HTTP local
 *         development), otherwise browsers may transmit the JWT cookies over an
 *         unencrypted connection.
 * @param cookieSameSite {@code Lax} works for local dev, where the frontend and this
 *         service are technically different ports but the same registrable domain
 *         (localhost). Once the frontend is deployed on a genuinely different domain
 *         (e.g. GitHub Pages) than this service (e.g. Render), {@code Lax} silently
 *         stops being sent on cross-site fetch/XHR requests at all — login appears to
 *         succeed (the cookie gets set) but every subsequent API call goes out without
 *         it. Must be {@code None} in that case, which in turn requires
 *         {@code cookieSecure=true} (browsers reject {@code SameSite=None} without
 *         {@code Secure}).
 */
@ConfigurationProperties(prefix = "booknest.app")
public record AppProperties(
        String frontendUrl,
        List<String> corsAllowedOrigins,
        boolean cookieSecure,
        String cookieSameSite
) {
}
