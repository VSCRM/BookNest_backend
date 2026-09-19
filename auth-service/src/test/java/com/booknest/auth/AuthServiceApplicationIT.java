package com.booknest.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the whole stack through {@link com.booknest.auth.config.SecurityConfig}'s
 * real filter chain instead of mocking it away: registration, cookie-based
 * authentication, refresh, and logout all run against an in-memory H2
 * database (see src/test/resources/application.yml). Kafka is mocked
 * because {@link com.booknest.auth.service.AuthEventPublisher} would
 * otherwise try to reach a real broker on every register/login call.
 *
 * <p>Request bodies are built as plain JSON strings rather than via an
 * injected {@code ObjectMapper}: Spring Boot 4's default web JSON stack is
 * Jackson 3 ({@code tools.jackson.databind.ObjectMapper}, from
 * spring-boot-jackson), not the classic {@code com.fasterxml.jackson}
 * ObjectMapper — no bean of that older type exists in this context, even
 * though the classic Jackson 2 databind jar happens to be on the classpath
 * transitively (pulled in by jjwt-jackson).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthServiceApplicationIT {

    private static final Pattern DEV_CODE_PATTERN = Pattern.compile("\"devCode\"\\s*:\\s*\"(\\d{6})\"");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeEach
    void stubKafkaSoNoRealBrokerIsContacted() {
        given(kafkaTemplate.send(anyString(), anyString(), any())).willReturn(new CompletableFuture<>());
    }

    private String uniqueEmail() {
        return "user-" + System.nanoTime() + "@booknest.test";
    }

    /** Hand-rolled JSON object builder so this test never needs a Jackson ObjectMapper bean. */
    private static String json(Map<String, String> fields) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(entry.getKey()).append("\":\"").append(entry.getValue()).append('"');
        }
        return sb.append('}').toString();
    }

    private static String json(String... keyValuePairs) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            fields.put(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return json(fields);
    }

    private static String extractDevCode(String responseBody) {
        Matcher matcher = DEV_CODE_PATTERN.matcher(responseBody);
        assertThat(matcher.find()).as("devCode field present in response: %s", responseBody).isTrue();
        return matcher.group(1);
    }

    @Test
    void contextLoads() {
        assertThat(mockMvc).isNotNull();
    }

    @Test
    void registerLoginMeRefreshLogoutHappyPath() throws Exception {
        String email = uniqueEmail();
        String registerBody = json("email", email, "password", "s3cret!!", "name", "Jane Reader");

        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(registerBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.user.username").value(email))
                .andReturn();

        Cookie accessCookie = registerResult.getResponse().getCookie("booknest_jwt");
        Cookie refreshCookie = registerResult.getResponse().getCookie("booknest_refresh");
        assertThat(accessCookie).isNotNull();
        assertThat(refreshCookie).isNotNull();

        // Protected endpoint rejects requests without the access-token cookie.
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        // ...and accepts them once the cookie minted at registration is presented.
        mockMvc.perform(get("/api/auth/me").cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.user.username").value(email));

        // Registering the same email twice is rejected with a 200/success:false envelope, not a 500.
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(registerBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("email_already_registered"));

        // Logging in with the wrong password fails without leaking whether the account exists.
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(json("email", email, "password", "wrong-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("invalid_credentials"));

        // Logging in with the right password succeeds.
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(json("email", email, "password", "s3cret!!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // The refresh cookie mints a fresh access token.
        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Logging out works even though it is unauthenticated w.r.t. Spring Security
        // permitAll, and clears both cookies.
        MvcResult logoutResult = mockMvc.perform(post("/api/auth/logout").cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        Cookie clearedAccess = logoutResult.getResponse().getCookie("booknest_jwt");
        assertThat(clearedAccess).isNotNull();
        assertThat(clearedAccess.getMaxAge()).isZero();
    }

    @Test
    void logoutWorksWithoutAnyAuthCookieAtAll() throws Exception {
        // This is the bug fix under test: logout used to require authentication,
        // so a client with an already-expired/missing cookie could never
        // successfully clear it.
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void registrationRejectsInvalidPayloadWithFieldLevelMessage() throws Exception {
        String invalidBody = json("email", "not-an-email", "password", "abc", "name", "");

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(invalidBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void meWithoutAuthenticationReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgotAndResetPasswordFlowIssuesNewCookiesOnSuccess() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(json("email", email, "password", "s3cret!!", "name", "Jane Reader")))
                .andExpect(status().isOk());

        MvcResult forgotResult = mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType("application/json")
                        .content(json("email", email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        String code = extractDevCode(forgotResult.getResponse().getContentAsString());
        assertThat(code).matches("\\d{6}");

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType("application/json")
                        .content(json("email", email, "code", code, "newPassword", "brandnew1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // The old password no longer works after a reset.
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(json("email", email, "password", "s3cret!!")))
                .andExpect(jsonPath("$.success").value(false));

        // The new one does.
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(json("email", email, "password", "brandnew1")))
                .andExpect(jsonPath("$.success").value(true));
    }
}
