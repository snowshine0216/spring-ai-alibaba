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

package com.alibaba.cloud.ai.studio.admin.controller;

import com.alibaba.cloud.ai.studio.admin.dto.Prompt;
import com.alibaba.cloud.ai.studio.admin.dto.PromptVersion;
import com.alibaba.cloud.ai.studio.admin.dto.PromptVersionDetail;
import com.alibaba.cloud.ai.studio.admin.service.PromptRunService;
import com.alibaba.cloud.ai.studio.admin.service.PromptService;
import com.alibaba.cloud.ai.studio.admin.service.PromptTemplateService;
import com.alibaba.cloud.ai.studio.admin.service.PromptVersionService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for {@link PromptController}.
 * P0-1: Verifies controller routes and Result wrapper code for prompt version endpoints.
 */
@ExtendWith(MockitoExtension.class)
class PromptControllerTest {

    @Mock
    private PromptService promptService;

    @Mock
    private PromptVersionService promptVersionService;

    @Mock
    private PromptTemplateService promptTemplateService;

    @Mock
    private PromptRunService promptRunService;

    @InjectMocks
    private PromptController promptController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // Register a Jackson converter so Result<T> is serialized as JSON, not as a string.
        mockMvc = MockMvcBuilders.standaloneSetup(promptController)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    // ---- POST /api/prompt/version ----

    @Test
    void createPromptVersion_validRequest_returnsIntegerCode200() throws Exception {
        // Arrange
        PromptVersion promptVersion = PromptVersion.builder()
                .promptKey("test-key")
                .version("1.0.0")
                .status("pre")
                .build();
        when(promptVersionService.create(any())).thenReturn(promptVersion);

        String requestBody = "{\"promptKey\":\"test-key\",\"version\":\"1.0.0\",\"template\":\"Hello {{name}}\",\"status\":\"pre\"}";

        // Act & Assert
        mockMvc.perform(post("/api/prompt/version")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                // The platform /api/** controllers use integer code (200 on success)
                .andExpect(jsonPath("$.code").isNumber())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.promptKey").value("test-key"))
                .andExpect(jsonPath("$.data.version").value("1.0.0"));
    }

    @Test
    void createPromptVersion_releaseStatus_returnsCode200() throws Exception {
        // Arrange
        PromptVersion promptVersion = PromptVersion.builder()
                .promptKey("my-prompt")
                .version("2.0.0")
                .status("release")
                .build();
        when(promptVersionService.create(any())).thenReturn(promptVersion);

        String requestBody = "{\"promptKey\":\"my-prompt\",\"version\":\"2.0.0\",\"template\":\"Hi\",\"status\":\"release\"}";

        // Act & Assert
        mockMvc.perform(post("/api/prompt/version")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").isNumber())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("release"));
    }

    // ---- GET /api/prompt/version?promptKey=...&version=... ----

    @Test
    void getPromptVersion_validParams_returnsVersionJson() throws Exception {
        // Arrange
        PromptVersionDetail detail = PromptVersionDetail.builder()
                .promptKey("test-key")
                .version("1.0.0")
                .template("Hello {{name}}")
                .status("pre")
                .build();
        when(promptVersionService.getByPromptKeyAndVersion(eq("test-key"), eq("1.0.0"))).thenReturn(detail);

        // Act & Assert
        mockMvc.perform(get("/api/prompt/version")
                        .param("promptKey", "test-key")
                        .param("version", "1.0.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").isNumber())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.promptKey").value("test-key"))
                .andExpect(jsonPath("$.data.version").value("1.0.0"))
                .andExpect(jsonPath("$.data.template").value("Hello {{name}}"));
    }

    @Test
    void getPromptVersion_resultCodeIsInteger_notString() throws Exception {
        // Arrange — verifies that the platform /api/** wrapper uses integer code, not string
        PromptVersionDetail detail = PromptVersionDetail.builder()
                .promptKey("k")
                .version("v1")
                .template("t")
                .status("pre")
                .build();
        when(promptVersionService.getByPromptKeyAndVersion(any(), any())).thenReturn(detail);

        // Act & Assert
        mockMvc.perform(get("/api/prompt/version")
                        .param("promptKey", "k")
                        .param("version", "v1"))
                .andExpect(status().isOk())
                // Integer (not string) — the platform split: /api/** uses integer 200
                .andExpect(jsonPath("$.code").isNumber());
    }

    // ---- POST /api/prompt ----

    @Test
    void createPrompt_validRequest_returnsCode200() throws Exception {
        // Arrange
        Prompt prompt = Prompt.builder().promptKey("new-key").build();
        when(promptService.create(any())).thenReturn(prompt);

        String requestBody = "{\"promptKey\":\"new-key\"}";

        // Act & Assert
        mockMvc.perform(post("/api/prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").isNumber())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.promptKey").value("new-key"));
    }

    // NOTE: POST /api/prompts/search is NOT defined in PromptController.
    // Per CLAUDE.md "Forbidden Areas", that path was previously exposed to the community
    // and must not be deleted. However it is not part of this controller's surface —
    // no test for its presence is added here to avoid asserting routes that do not exist.
}
