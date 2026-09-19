package com.booknest.auth.security;

import com.booknest.auth.config.AppProperties;
import com.booknest.auth.domain.AuthProvider;
import com.booknest.auth.domain.User;
import com.booknest.auth.service.AuthEventPublisher;
import com.booknest.auth.service.JwtService;
import com.booknest.auth.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginSuccessHandlerTest {

    @Mock
    private UserService userService;
    @Mock
    private JwtService jwtService;
    @Mock
    private CookieUtil cookieUtil;
    @Mock
    private AuthEventPublisher eventPublisher;

    private final AppProperties appProperties = new AppProperties("http://localhost:5174", List.of(), false, "Lax");

    private OAuth2LoginSuccessHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OAuth2LoginSuccessHandler(userService, jwtService, cookieUtil, appProperties, eventPublisher);
    }

    private static OAuth2User googleUser(String email, String name) {
        Map<String, Object> attributes = new java.util.HashMap<>();
        if (email != null) attributes.put("email", email);
        if (name != null) attributes.put("name", name);
        attributes.putIfAbsent("sub", "google-subject-id");
        return new DefaultOAuth2User(List.of(() -> "ROLE_USER"), attributes, "sub");
    }

    @Test
    void findsOrCreatesUserIssuesCookiesAndRedirectsToFrontend() throws Exception {
        OAuth2User oAuth2User = googleUser("jane@booknest.test", "Jane Reader");
        Authentication authentication = new TestingAuthenticationToken(oAuth2User, null);
        User user = User.builder().email("jane@booknest.test").name("Jane Reader").provider(AuthProvider.GOOGLE).build();
        given(userService.findOrCreateGoogleUser("jane@booknest.test", "Jane Reader")).willReturn(user);
        given(jwtService.generateAccessToken(user)).willReturn("access-token");
        given(jwtService.generateRefreshToken(user)).willReturn("refresh-token");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(cookieUtil).writeAuthCookies(response, "access-token", "refresh-token");
        verify(eventPublisher).publishUserLoggedIn(user);
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5174/profile");
    }

    @Test
    void redirectsToThePathCapturedByCustomAuthorizationRequestResolverWhenPresent() throws Exception {
        OAuth2User oAuth2User = googleUser("jane@booknest.test", "Jane Reader");
        Authentication authentication = new TestingAuthenticationToken(oAuth2User, null);
        User user = User.builder().email("jane@booknest.test").name("Jane Reader").provider(AuthProvider.GOOGLE).build();
        given(userService.findOrCreateGoogleUser("jane@booknest.test", "Jane Reader")).willReturn(user);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute(
                CustomAuthorizationRequestResolver.POST_LOGIN_REDIRECT_SESSION_KEY, "/cart");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        // Lets a checkout interrupted by "please log in" resume automatically:
        // CartPage only picks that back up from its own mount effect, which
        // never runs if Google login always lands on /profile instead.
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5174/cart");
        assertThat(request.getSession(false).getAttribute(
                CustomAuthorizationRequestResolver.POST_LOGIN_REDIRECT_SESSION_KEY)).isNull();
    }

    @Test
    void ignoresAnAbsoluteRedirectAndFallsBackToProfile() throws Exception {
        OAuth2User oAuth2User = googleUser("jane@booknest.test", "Jane Reader");
        Authentication authentication = new TestingAuthenticationToken(oAuth2User, null);
        User user = User.builder().email("jane@booknest.test").name("Jane Reader").provider(AuthProvider.GOOGLE).build();
        given(userService.findOrCreateGoogleUser("jane@booknest.test", "Jane Reader")).willReturn(user);
        MockHttpServletRequest request = new MockHttpServletRequest();
        // Nothing sanitizes on the way *into* the session in this test, so
        // this stands in for e.g. a tampered/forged session attribute — the
        // handler itself must still refuse to redirect off-site.
        request.getSession(true).setAttribute(
                CustomAuthorizationRequestResolver.POST_LOGIN_REDIRECT_SESSION_KEY, "https://evil.example/phish");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5174/profile");
    }

    @Test
    void fallsBackToEmailAsNameWhenGoogleDoesNotReturnAName() throws Exception {
        OAuth2User oAuth2User = googleUser("jane@booknest.test", null);
        Authentication authentication = new TestingAuthenticationToken(oAuth2User, null);
        User user = User.builder().email("jane@booknest.test").name("jane@booknest.test").provider(AuthProvider.GOOGLE).build();
        given(userService.findOrCreateGoogleUser("jane@booknest.test", "jane@booknest.test")).willReturn(user);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(userService).findOrCreateGoogleUser("jane@booknest.test", "jane@booknest.test");
    }

    @Test
    void redirectsWithErrorWhenGoogleReturnsNoEmail() throws Exception {
        OAuth2User oAuth2User = googleUser(null, "Jane Reader");
        Authentication authentication = new TestingAuthenticationToken(oAuth2User, null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5174/login?error=google_no_email");
        verify(userService, never()).findOrCreateGoogleUser(any(), any());
        verify(cookieUtil, never()).writeAuthCookies(any(), any(), any());
    }
}
