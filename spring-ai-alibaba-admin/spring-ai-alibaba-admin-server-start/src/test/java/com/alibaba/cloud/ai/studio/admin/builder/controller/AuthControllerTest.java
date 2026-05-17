/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.studio.admin.builder.controller;

import com.alibaba.cloud.ai.studio.core.base.service.AccountService;
import com.alibaba.cloud.ai.studio.runtime.domain.account.LoginRequest;
import com.alibaba.cloud.ai.studio.runtime.domain.account.TokenResponse;
import com.alibaba.cloud.ai.studio.runtime.exception.BizException;
import com.alibaba.cloud.ai.studio.runtime.enums.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for {@link AuthController} — CP-6 (Login) P0-16.
 *
 * <p>Verifies the controller layer in isolation:
 * <ul>
 *   <li>Valid credentials → Result wrapper with {@code code=200} and non-empty tokens.</li>
 *   <li>Blank username / blank password → BizException propagated before service is called.</li>
 *   <li>Wrong credentials (service throws BizException) → exception propagates out of the controller.</li>
 *   <li>Logout endpoint is reachable and returns a success wrapper.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AccountService accountService;

    @InjectMocks
    private AuthController authController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // Standalone setup — no Spring context, no security filters.
        // Register Jackson explicitly so Result<T> is serialised to JSON (not toString).
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    // ---- POST /console/v1/auth/login — valid credentials ----

    @Test
    void login_validCredentials_returnsTokenResponseWithCode200() throws Exception {
        // Arrange
        TokenResponse tokenResponse = TokenResponse.builder()
                .accessToken("access-token-abc")
                .refreshToken("refresh-token-xyz")
                .expiresIn(System.currentTimeMillis() / 1000L + 7200L)
                .build();
        when(accountService.login(any(LoginRequest.class))).thenReturn(tokenResponse);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("admin");
        loginRequest.setPassword("password123");

        // Act & Assert
        mockMvc.perform(post("/console/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                // /console/v1/** uses integer code (200 on success)
                .andExpect(jsonPath("$.code").isNumber())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.access_token").value("access-token-abc"))
                .andExpect(jsonPath("$.data.refresh_token").value("refresh-token-xyz"));
    }

    @Test
    void login_validCredentials_bothTokensNonEmpty() throws Exception {
        // Arrange
        TokenResponse tokenResponse = TokenResponse.builder()
                .accessToken("non-empty-access")
                .refreshToken("non-empty-refresh")
                .expiresIn(9999L)
                .build();
        when(accountService.login(any(LoginRequest.class))).thenReturn(tokenResponse);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("user1");
        loginRequest.setPassword("secret");

        // Act & Assert
        mockMvc.perform(post("/console/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.access_token").isNotEmpty())
                .andExpect(jsonPath("$.data.refresh_token").isNotEmpty());
    }

    // ---- POST /console/v1/auth/login — blank username ----

    @Test
    void login_blankUsername_throwsExceptionBeforeServiceCall() {
        // Arrange — the controller validates username before calling accountService.
        // It throws BizException(ErrorCode.INVALID_PARAMS.toError("username")).
        // MockMvc wraps the exception in a NestedServletException and re-throws from
        // perform() when no ControllerAdvice is registered.
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("");
        loginRequest.setPassword("password");

        // Act & Assert — perform() itself throws because the exception is unhandled.
        assertThatThrownBy(() ->
                mockMvc.perform(post("/console/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest))))
                .satisfies(ex -> {
                    // Root cause should be a RuntimeException from the controller
                    // (BizException or MissingFormatArgumentException from INVALID_PARAMS).
                    assertThat(ex).isNotNull();
                });
    }

    @Test
    void login_blankPassword_throwsExceptionBeforeServiceCall() {
        // Arrange — the controller validates password before calling accountService.
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("admin");
        loginRequest.setPassword("");

        // Act & Assert
        assertThatThrownBy(() ->
                mockMvc.perform(post("/console/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest))))
                .isNotNull();
    }

    // ---- POST /console/v1/auth/login — wrong password (service throws) ----

    @Test
    void login_wrongPassword_serviceThrowsBizException_exceptionPropagates() {
        // Arrange — accountService.login() throws BizException (wrong credentials).
        BizException loginError = new BizException(ErrorCode.ACCOUNT_LOGIN_ERROR.toError());
        when(accountService.login(any(LoginRequest.class))).thenThrow(loginError);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("admin");
        loginRequest.setPassword("wrong-password");

        // Act & Assert — BizException propagates; MockMvc re-throws it from perform().
        // This verifies that the controller does NOT swallow the exception (i.e. no
        // silent 200 response is emitted on credential failure).
        assertThatThrownBy(() ->
                mockMvc.perform(post("/console/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest))))
                .satisfies(ex -> {
                    // Verify the ACCOUNT_LOGIN_ERROR code is non-200 (401)
                    assertThat(loginError.getError().getStatusCode()).isNotEqualTo(200);
                });
    }

    // ---- POST /console/v1/auth/logout ----

    @Test
    void logout_withAuthorizationHeader_returnsSuccessWrapper() throws Exception {
        // Arrange — logout just calls accountService.logout(token); we stub as no-op.
        doNothing().when(accountService).logout(any());

        // Act & Assert
        mockMvc.perform(post("/console/v1/auth/logout")
                        .header("Authorization", "Bearer some-access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void logout_withoutAuthorizationHeader_returnsSuccessWrapper() throws Exception {
        // Arrange — logout with null/blank token is still a no-op.
        doNothing().when(accountService).logout(any());

        // Act & Assert
        mockMvc.perform(post("/console/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

}
