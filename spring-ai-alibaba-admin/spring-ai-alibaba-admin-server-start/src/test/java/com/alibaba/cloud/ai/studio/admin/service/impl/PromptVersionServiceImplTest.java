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

package com.alibaba.cloud.ai.studio.admin.service.impl;

import com.alibaba.cloud.ai.studio.admin.dto.PromptVersion;
import com.alibaba.cloud.ai.studio.admin.dto.request.PromptVersionCreateRequest;
import com.alibaba.cloud.ai.studio.admin.entity.PromptDO;
import com.alibaba.cloud.ai.studio.admin.entity.PromptVersionDO;
import com.alibaba.cloud.ai.studio.admin.exception.StudioException;
import com.alibaba.cloud.ai.studio.admin.mapper.PromptMapper;
import com.alibaba.cloud.ai.studio.admin.mapper.PromptVersionMapper;
import com.alibaba.cloud.ai.studio.admin.service.PromptService;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PromptVersionServiceImpl}.
 * P0-5: create throws NOT_FOUND when prompt key doesn't exist.
 * P0-5: create throws CONFLICT when trying to publish a release version that already exists.
 * P0-6: create calls insert once then publishConfig when status=release.
 * P0-6: create with status=pre does NOT call publishConfig.
 * P0-6: Nacos dataId format and group constant verification.
 * P0-6: NacosException in publishConfig is swallowed — call completes normally.
 */
@ExtendWith(MockitoExtension.class)
class PromptVersionServiceImplTest {

    @Mock
    private PromptVersionMapper promptVersionMapper;

    @Mock
    private PromptMapper promptMapper;

    @Mock
    private PromptService promptService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private NacosClientService nacosClientService;

    @Mock
    private ConfigService configService;

    @InjectMocks
    private PromptVersionServiceImpl promptVersionService;

    // ---- Helper to build a minimal valid request ----

    private PromptVersionCreateRequest buildRequest(String promptKey, String version, String status) {
        PromptVersionCreateRequest req = new PromptVersionCreateRequest();
        req.setPromptKey(promptKey);
        req.setVersion(version);
        req.setTemplate("Hello {{name}}");
        req.setStatus(status);
        return req;
    }

    // ---- create: prompt key does not exist → NOT_FOUND ----

    @Test
    void create_promptKeyNotFound_throwsNotFoundException() {
        // Arrange
        when(promptMapper.selectByPromptKey("unknown")).thenReturn(null);

        // Act & Assert
        assertThatThrownBy(() -> promptVersionService.create(buildRequest("unknown", "1.0", "pre")))
                .isInstanceOf(StudioException.class)
                .satisfies(ex -> {
                    StudioException studio = (StudioException) ex;
                    assertThat(studio.getErrCode()).isEqualTo(StudioException.NOT_FOUND);
                });

        verify(promptVersionMapper, never()).insert(any());
    }

    // ---- create: release version already exists → CONFLICT ----

    @Test
    void create_releaseVersionAlreadyExists_throwsConflictException() {
        // Arrange
        PromptDO existingPrompt = PromptDO.builder().promptKey("my-key").build();
        when(promptMapper.selectByPromptKey("my-key")).thenReturn(existingPrompt);
        when(promptVersionMapper.existsByPromptKeyAndVersion("my-key", "1.0")).thenReturn(true);
        when(promptVersionMapper.selectStatusByPromptKeyAndVersion("my-key", "1.0")).thenReturn("release");

        // Act & Assert — trying to publish a second "release" for same version
        assertThatThrownBy(() -> promptVersionService.create(buildRequest("my-key", "1.0", "release")))
                .isInstanceOf(StudioException.class)
                .satisfies(ex -> {
                    StudioException studio = (StudioException) ex;
                    assertThat(studio.getErrCode()).isEqualTo(StudioException.CONFLICT);
                });

        verify(promptVersionMapper, never()).insert(any());
    }

    @Test
    void create_preOnExistingReleaseVersion_throwsConflictException() {
        // Arrange — existing is "release"; trying to create "pre" for same version
        PromptDO existingPrompt = PromptDO.builder().promptKey("my-key").build();
        when(promptMapper.selectByPromptKey("my-key")).thenReturn(existingPrompt);
        when(promptVersionMapper.existsByPromptKeyAndVersion("my-key", "1.0")).thenReturn(true);
        when(promptVersionMapper.selectStatusByPromptKeyAndVersion("my-key", "1.0")).thenReturn("release");

        assertThatThrownBy(() -> promptVersionService.create(buildRequest("my-key", "1.0", "pre")))
                .isInstanceOf(StudioException.class)
                .satisfies(ex -> assertThat(((StudioException) ex).getErrCode()).isEqualTo(StudioException.CONFLICT));
    }

    // ---- create with status=release: insert then publishConfig ----

    @Test
    void create_releaseStatus_callsInsertAndPublishConfig() throws Exception {
        // Arrange
        PromptDO existingPrompt = PromptDO.builder().promptKey("pub-key").build();
        when(promptMapper.selectByPromptKey("pub-key")).thenReturn(existingPrompt);
        when(promptVersionMapper.existsByPromptKeyAndVersion("pub-key", "2.0")).thenReturn(false);
        when(promptVersionMapper.selectLatestVersion("pub-key")).thenReturn(null);
        when(promptVersionMapper.insert(any())).thenReturn(1);
        when(nacosClientService.getConfigService()).thenReturn(configService);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"promptKey\":\"pub-key\"}");
        when(configService.publishConfig(anyString(), anyString(), anyString())).thenReturn(true);

        // Act
        PromptVersion result = promptVersionService.create(buildRequest("pub-key", "2.0", "release"));

        // Assert — insert called exactly once
        verify(promptVersionMapper).insert(any(PromptVersionDO.class));
        // publishConfig called with correct dataId and group
        verify(configService).publishConfig(
                eq("prompt-pub-key.json"),
                eq("nacos-ai-meta"),
                anyString());
        assertThat(result).isNotNull();
    }

    @Test
    void create_releaseStatus_nacosDataIdFollowsConvention() throws Exception {
        // The dataId must be "prompt-<promptKey>.json" and group must be "nacos-ai-meta"
        PromptDO existingPrompt = PromptDO.builder().promptKey("my-prompt").build();
        when(promptMapper.selectByPromptKey("my-prompt")).thenReturn(existingPrompt);
        when(promptVersionMapper.existsByPromptKeyAndVersion("my-prompt", "3.0")).thenReturn(false);
        when(promptVersionMapper.selectLatestVersion("my-prompt")).thenReturn("2.0");
        when(promptVersionMapper.insert(any())).thenReturn(1);
        when(nacosClientService.getConfigService()).thenReturn(configService);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(configService.publishConfig(anyString(), anyString(), anyString())).thenReturn(true);

        promptVersionService.create(buildRequest("my-prompt", "3.0", "release"));

        verify(configService).publishConfig(
                eq("prompt-my-prompt.json"),
                eq("nacos-ai-meta"),
                anyString());
    }

    // ---- create with status=pre: does NOT call publishConfig ----

    @Test
    void create_preStatus_doesNotCallPublishConfig() throws Exception {
        // Arrange
        PromptDO existingPrompt = PromptDO.builder().promptKey("draft-key").build();
        when(promptMapper.selectByPromptKey("draft-key")).thenReturn(existingPrompt);
        when(promptVersionMapper.existsByPromptKeyAndVersion("draft-key", "1.0-SNAPSHOT")).thenReturn(false);
        when(promptVersionMapper.selectLatestVersion("draft-key")).thenReturn(null);
        when(promptVersionMapper.insert(any())).thenReturn(1);

        // Act
        promptVersionService.create(buildRequest("draft-key", "1.0-SNAPSHOT", "pre"));

        // Assert — publishConfig must never be called for pre-release
        verify(configService, never()).publishConfig(anyString(), anyString(), anyString());
        // and nacosClientService.getConfigService() is never called
        verify(nacosClientService, never()).getConfigService();
        // insert must be called
        verify(promptVersionMapper).insert(any(PromptVersionDO.class));
    }

    // ---- NacosException swallowed: call still completes ----

    @Test
    void create_releaseStatus_nacosExceptionIsSwallowedAndCallCompletes() throws Exception {
        /*
         * Known footgun: publishPromptToNacos catches NacosException and only logs it —
         * it does NOT propagate the exception. This means callers cannot detect a Nacos
         * publish failure from the return value alone. The test documents and verifies this
         * current behavior so that any future change to propagate the exception will be
         * caught as a breaking-behavior change.
         */
        PromptDO existingPrompt = PromptDO.builder().promptKey("flaky-key").build();
        when(promptMapper.selectByPromptKey("flaky-key")).thenReturn(existingPrompt);
        when(promptVersionMapper.existsByPromptKeyAndVersion("flaky-key", "1.0")).thenReturn(false);
        when(promptVersionMapper.selectLatestVersion("flaky-key")).thenReturn(null);
        when(promptVersionMapper.insert(any())).thenReturn(1);
        when(nacosClientService.getConfigService()).thenReturn(configService);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        doThrow(new NacosException(500, "simulated nacos error"))
                .when(configService).publishConfig(anyString(), anyString(), anyString());

        // Act — must NOT throw even though Nacos fails
        PromptVersion result = promptVersionService.create(buildRequest("flaky-key", "1.0", "release"));

        // Assert — insert still committed, call returned normally
        verify(promptVersionMapper).insert(any(PromptVersionDO.class));
        assertThat(result).isNotNull();
    }
}
