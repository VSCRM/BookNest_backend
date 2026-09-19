package com.booknest.auth.security;

import com.booknest.auth.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2LoginFailureHandlerTest {

    private final AppProperties appProperties = new AppProperties("http://localhost:5174", List.of(), false, "Lax");
    private final OAuth2LoginFailureHandler handler = new OAuth2LoginFailureHandler(appProperties);

    @Test
    void redirectsWithUrlEncodedFailureReason() throws Exception {
        AuthenticationException exception = new OAuth2AuthenticationException(
                new OAuth2Error("access_denied"), "access denied by user");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, exception);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5174/login?error=access+denied+by+user");
    }

    @Test
    void fallsBackToGenericReasonWhenExceptionMessageIsNull() throws Exception {
        AuthenticationException exception = new OAuth2AuthenticationException(new OAuth2Error("server_error"), (String) null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, exception);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5174/login?error=oauth2_login_failed");
    }
}
