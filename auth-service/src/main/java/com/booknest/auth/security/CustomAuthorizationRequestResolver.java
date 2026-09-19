package com.booknest.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Wraps the default resolver purely to capture an optional {@code
 * redirect_uri} query parameter off the initial hit to
 * {@code /oauth2/authorization/google} (e.g.
 * {@code /oauth2/authorization/google?redirect_uri=/cart}), stashing it in
 * the HttpSession so {@link OAuth2LoginSuccessHandler} can send the browser
 * back to wherever the person actually was — resuming an in-progress
 * checkout, for instance — instead of always landing on /profile.
 *
 * The frontend's storefront origin and this service run as two separate
 * apps, so there is no client-side router state to carry across the
 * full-page redirect through Google and back; the HttpSession set up here
 * (created for exactly this purpose — the rest of this service is stateless,
 * see {@code SecurityConfig}) is what bridges that gap.
 */
public class CustomAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

	/** Session attribute key read back by {@link OAuth2LoginSuccessHandler}. */
	public static final String POST_LOGIN_REDIRECT_SESSION_KEY = "post_login_redirect_uri";

	private static final String AUTHORIZATION_REQUEST_BASE_URI = "/oauth2/authorization";

	private final DefaultOAuth2AuthorizationRequestResolver delegate;

	public CustomAuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
		this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
				clientRegistrationRepository, AUTHORIZATION_REQUEST_BASE_URI);
	}

	@Override
	public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
		captureRedirect(request);
		return delegate.resolve(request);
	}

	@Override
	public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String registrationId) {
		captureRedirect(request);
		return delegate.resolve(request, registrationId);
	}

	private void captureRedirect(HttpServletRequest request) {
		String redirectUri = sanitize(request.getParameter("redirect_uri"));
		if (redirectUri == null) {
			return;
		}

		HttpSession session = request.getSession(true);
		session.setAttribute(POST_LOGIN_REDIRECT_SESSION_KEY, redirectUri);
	}

	/**
	 * Only accepts a same-origin path: one that starts with exactly one
	 * "/" and nothing else that a browser would treat as the start of an
	 * absolute URL. This value ends up in a server-issued 302 later, so
	 * anything looser here (e.g. "//evil.com" or "https://evil.com") would
	 * be an open-redirect vulnerability.
	 */
	static String sanitize(String candidate) {
		if (candidate == null || candidate.isBlank()) {
			return null;
		}
		if (!candidate.startsWith("/") || candidate.startsWith("//")) {
			return null;
		}
		return candidate;
	}
}
