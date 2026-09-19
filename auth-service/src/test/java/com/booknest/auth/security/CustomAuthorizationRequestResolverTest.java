package com.booknest.auth.security;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.OidcScopes;

import static org.assertj.core.api.Assertions.assertThat;

class CustomAuthorizationRequestResolverTest {

	private static ClientRegistrationRepository clientRegistrationRepository() {
		ClientRegistration registration = ClientRegistration.withRegistrationId("google")
				.clientId("client-id")
				.clientSecret("client-secret")
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
				.authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
				.tokenUri("https://oauth2.googleapis.com/token")
				.scope(OidcScopes.OPENID, OidcScopes.EMAIL, OidcScopes.PROFILE)
				.build();
		return new InMemoryClientRegistrationRepository(registration);
	}

	@Test
	void resolveWithoutRegistrationIdStashesSanitizedRedirectUriInSession() {
		CustomAuthorizationRequestResolver resolver =
				new CustomAuthorizationRequestResolver(clientRegistrationRepository());
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/google");
		request.setServletPath("/oauth2/authorization/google");
		request.addParameter("redirect_uri", "/cart");

		resolver.resolve(request);

		HttpSession session = request.getSession(false);
		assertThat(session).isNotNull();
		assertThat(session.getAttribute(CustomAuthorizationRequestResolver.POST_LOGIN_REDIRECT_SESSION_KEY))
				.isEqualTo("/cart");
	}

	@Test
	void resolveWithoutRegistrationIdLeavesSessionUntouchedWhenNoRedirectUriGiven() {
		CustomAuthorizationRequestResolver resolver =
				new CustomAuthorizationRequestResolver(clientRegistrationRepository());
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/google");
		request.setServletPath("/oauth2/authorization/google");

		resolver.resolve(request);

		assertThat(request.getSession(false)).isNull();
	}

	@Test
	void resolveWithRegistrationIdAlsoCapturesRedirectUri() {
		CustomAuthorizationRequestResolver resolver =
				new CustomAuthorizationRequestResolver(clientRegistrationRepository());
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/google");
		request.addParameter("redirect_uri", "/orders/42");

		resolver.resolve(request, "google");

		HttpSession session = request.getSession(false);
		assertThat(session).isNotNull();
		assertThat(session.getAttribute(CustomAuthorizationRequestResolver.POST_LOGIN_REDIRECT_SESSION_KEY))
				.isEqualTo("/orders/42");
	}

	@Test
	void resolveWithRegistrationIdIgnoresAnOpenRedirectAttempt() {
		CustomAuthorizationRequestResolver resolver =
				new CustomAuthorizationRequestResolver(clientRegistrationRepository());
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/google");
		request.addParameter("redirect_uri", "https://evil.example/phish");

		resolver.resolve(request, "google");

		assertThat(request.getSession(false)).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {"/cart", "/", "/orders/42", "/book/some-slug?x=1"})
	void acceptsRelativeSameOriginPaths(String candidate) {
		assertThat(CustomAuthorizationRequestResolver.sanitize(candidate)).isEqualTo(candidate);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {
			"  ",
			"//evil.example",
			"http://evil.example",
			"https://evil.example/phish",
			"cart",
			"javascript:alert(1)",
	})
	void rejectsAnythingThatIsNotAPlainRelativePath(String candidate) {
		assertThat(CustomAuthorizationRequestResolver.sanitize(candidate)).isNull();
	}
}
