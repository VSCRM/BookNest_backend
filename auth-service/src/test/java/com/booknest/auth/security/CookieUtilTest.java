package com.booknest.auth.security;

import com.booknest.auth.config.AppProperties;
import com.booknest.auth.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CookieUtilTest {

    @Mock
    private JwtService jwtService;

    private AppProperties insecureProperties() {
        return new AppProperties("http://localhost:5174", List.of("http://localhost:5174"), false, "Lax");
    }

    private AppProperties secureProperties() {
        return new AppProperties("https://booknest.example", List.of("https://booknest.example"), true, "None");
    }

    @Test
    void writeAuthCookiesSetsBothCookiesWithExpectedAttributes() {
        given(jwtService.accessTokenMaxAgeSeconds()).willReturn(900L);
        given(jwtService.refreshTokenMaxAgeSeconds()).willReturn(604800L);
        CookieUtil cookieUtil = new CookieUtil(jwtService, insecureProperties());
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookieUtil.writeAuthCookies(response, "access-token-value", "refresh-token-value");

        List<String> cookies = response.getHeaders("Set-Cookie");
        assertThat(cookies).hasSize(2);

        String accessCookie = cookies.get(0);
        assertThat(accessCookie).contains("booknest_jwt=access-token-value");
        assertThat(accessCookie).contains("Path=/");
        assertThat(accessCookie).contains("Max-Age=900");
        assertThat(accessCookie).containsIgnoringCase("HttpOnly");
        assertThat(accessCookie).containsIgnoringCase("SameSite=Lax");
        assertThat(accessCookie).doesNotContainIgnoringCase("Secure");

        String refreshCookie = cookies.get(1);
        assertThat(refreshCookie).contains("booknest_refresh=refresh-token-value");
        assertThat(refreshCookie).contains("Path=/api/auth/refresh");
        assertThat(refreshCookie).contains("Max-Age=604800");
    }

    @Test
    void cookiesAreMarkedSecureWhenConfigured() {
        given(jwtService.accessTokenMaxAgeSeconds()).willReturn(900L);
        given(jwtService.refreshTokenMaxAgeSeconds()).willReturn(604800L);
        CookieUtil cookieUtil = new CookieUtil(jwtService, secureProperties());
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookieUtil.writeAuthCookies(response, "access", "refresh");

        List<String> cookies = response.getHeaders("Set-Cookie");
        assertThat(cookies).allSatisfy(cookie -> assertThat(cookie).containsIgnoringCase("Secure"));
    }

    @Test
    void clearAuthCookiesExpiresBothCookiesImmediately() {
        CookieUtil cookieUtil = new CookieUtil(jwtService, insecureProperties());
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookieUtil.clearAuthCookies(response);

        List<String> cookies = response.getHeaders("Set-Cookie");
        assertThat(cookies).hasSize(2);
        assertThat(cookies.get(0)).contains("booknest_jwt=").contains("Max-Age=0");
        assertThat(cookies.get(1)).contains("booknest_refresh=").contains("Max-Age=0");
    }
}
