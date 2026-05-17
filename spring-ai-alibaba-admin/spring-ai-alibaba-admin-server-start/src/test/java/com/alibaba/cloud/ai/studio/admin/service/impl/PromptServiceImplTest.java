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

import com.alibaba.cloud.ai.studio.admin.dto.Prompt;
import com.alibaba.cloud.ai.studio.admin.dto.request.PromptCreateRequest;
import com.alibaba.cloud.ai.studio.admin.entity.PromptDO;
import com.alibaba.cloud.ai.studio.admin.exception.StudioException;
import com.alibaba.cloud.ai.studio.admin.mapper.PromptMapper;
import com.alibaba.cloud.ai.studio.admin.mapper.PromptVersionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PromptServiceImpl}.
 * P0-3: create rejects duplicate promptKey (CONFLICT).
 * P0-4: deleteByPromptKey is idempotent; updateLatestVersion calls mapper; getByPromptKey
 *        throws NOT_FOUND when absent.
 */
@ExtendWith(MockitoExtension.class)
class PromptServiceImplTest {

    @Mock
    private PromptMapper promptMapper;

    @Mock
    private PromptVersionMapper promptVersionMapper;

    @InjectMocks
    private PromptServiceImpl promptService;

    // ---- create: duplicate key → CONFLICT ----

    @Test
    void create_duplicatePromptKey_throwsConflictException() {
        // Arrange
        PromptCreateRequest request = new PromptCreateRequest();
        request.setPromptKey("existing-key");

        PromptDO existing = PromptDO.builder().promptKey("existing-key").build();
        when(promptMapper.selectByPromptKey("existing-key")).thenReturn(existing);

        // Act & Assert
        assertThatThrownBy(() -> promptService.create(request))
                .isInstanceOf(StudioException.class)
                .satisfies(ex -> {
                    StudioException studio = (StudioException) ex;
                    assertThat(studio.getErrCode()).isEqualTo(StudioException.CONFLICT);
                });

        // insert must NOT be called when key already exists
        verify(promptMapper, never()).insert(any());
    }

    @Test
    void create_newPromptKey_callsInsertAndReturnsPrompt() throws StudioException {
        // Arrange
        PromptCreateRequest request = new PromptCreateRequest();
        request.setPromptKey("new-key");
        request.setPromptDescription("A new prompt");

        when(promptMapper.selectByPromptKey("new-key")).thenReturn(null);
        when(promptMapper.insert(any())).thenReturn(1);

        // Act
        Prompt result = promptService.create(request);

        // Assert
        verify(promptMapper).insert(any(PromptDO.class));
        assertThat(result).isNotNull();
        assertThat(result.getPromptKey()).isEqualTo("new-key");
    }

    // ---- getByPromptKey: absent → NOT_FOUND ----

    @Test
    void getByPromptKey_absentKey_throwsNotFoundException() {
        // Arrange
        when(promptMapper.selectByPromptKeyWithLatestVersionStatus("missing")).thenReturn(null);

        // Act & Assert
        assertThatThrownBy(() -> promptService.getByPromptKey("missing"))
                .isInstanceOf(StudioException.class)
                .satisfies(ex -> {
                    StudioException studio = (StudioException) ex;
                    assertThat(studio.getErrCode()).isEqualTo(StudioException.NOT_FOUND);
                });
    }

    @Test
    void getByPromptKey_existingKey_returnsPrompt() throws StudioException {
        // Arrange
        Map<String, Object> row = new HashMap<>();
        row.put("prompt_key", "my-key");
        row.put("prompt_desc", "desc");
        row.put("latest_version", "1.0");
        row.put("latest_version_status", "release");
        row.put("tags", "tag1");
        row.put("create_time", null);
        row.put("update_time", null);

        when(promptMapper.selectByPromptKeyWithLatestVersionStatus("my-key")).thenReturn(row);

        // Act
        Prompt result = promptService.getByPromptKey("my-key");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getPromptKey()).isEqualTo("my-key");
        assertThat(result.getLatestVersion()).isEqualTo("1.0");
    }

    // ---- deleteByPromptKey: idempotent when absent ----

    @Test
    void deleteByPromptKey_absentKey_returnsWithoutError() throws StudioException {
        // Arrange — prompt does not exist
        when(promptMapper.selectByPromptKey("ghost-key")).thenReturn(null);

        // Act — must not throw
        promptService.deleteByPromptKey("ghost-key");

        // Assert — neither the version delete nor the prompt delete is called
        verify(promptVersionMapper, never()).deleteByPromptKey(any());
        verify(promptMapper, never()).deleteByPromptKey(any());
    }

    @Test
    void deleteByPromptKey_existingKey_deletesVersionsThenPrompt() throws StudioException {
        // Arrange
        PromptDO existing = PromptDO.builder().promptKey("live-key").build();
        when(promptMapper.selectByPromptKey("live-key")).thenReturn(existing);
        when(promptVersionMapper.deleteByPromptKey("live-key")).thenReturn(3);
        when(promptMapper.deleteByPromptKey("live-key")).thenReturn(1);

        // Act
        promptService.deleteByPromptKey("live-key");

        // Assert — versions deleted first, then the prompt itself
        verify(promptVersionMapper).deleteByPromptKey("live-key");
        verify(promptMapper).deleteByPromptKey("live-key");
    }

    // ---- updateLatestVersion: delegates to mapper ----

    @Test
    void updateLatestVersion_callsMapperWithCorrectArgs() {
        // Arrange
        when(promptMapper.updateLatestVersion(eq("k"), eq("v2"))).thenReturn(1);

        // Act
        promptService.updateLatestVersion("k", "v2");

        // Assert
        verify(promptMapper).updateLatestVersion("k", "v2");
    }
}
