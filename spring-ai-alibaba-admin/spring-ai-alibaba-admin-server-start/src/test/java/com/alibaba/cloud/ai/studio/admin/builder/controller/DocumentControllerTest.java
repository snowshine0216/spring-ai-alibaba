/*
 * Copyright 2025 the original author or authors.
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

import com.alibaba.cloud.ai.studio.core.context.RequestContextHolder;
import com.alibaba.cloud.ai.studio.core.rag.DocumentService;
import com.alibaba.cloud.ai.studio.runtime.domain.RequestContext;
import com.alibaba.cloud.ai.studio.runtime.domain.knowledgebase.CreateDocumentRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for {@link DocumentController} — CP-7 P0-19.
 *
 * <p>Verifies that POST /console/v1/knowledge-bases/{kbId}/documents:
 * <ul>
 *   <li>returns HTTP 200 with integer {@code code=200} in the Result wrapper</li>
 *   <li>delegates to {@link DocumentService#createDocuments} with the correct kbId</li>
 *   <li>returns the list of created document ids in {@code data}</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class DocumentControllerTest {

    @Mock
    private DocumentService documentService;

    @InjectMocks
    private DocumentController documentController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // Register a Jackson converter so Result<T> is serialized as JSON, not as a string.
        mockMvc = MockMvcBuilders.standaloneSetup(documentController)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        // Seed a minimal RequestContext so the controller can call getRequestContext()
        RequestContext ctx = new RequestContext();
        ctx.setRequestId("req-test-001");
        ctx.setWorkspaceId("ws-test");
        ctx.setAccountId("account-test");
        RequestContextHolder.setRequestContext(ctx);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clearRequestContext();
    }

    // -----------------------------------------------------------------------
    // POST /console/v1/knowledge-bases/{kbId}/documents — happy path
    // -----------------------------------------------------------------------

    /**
     * P0-19: createDocuments — Result wrapper uses integer code (200 for /console/v1/**)
     * and data field contains the returned doc-id list.
     */
    @Test
    void createDocuments_validRequest_returnsIntegerCode200AndDocIds() throws Exception {
        // Arrange
        String kbId = "kb-123";
        List<String> returnedIds = List.of("doc-id-1", "doc-id-2");
        when(documentService.createDocuments(any(CreateDocumentRequest.class))).thenReturn(returnedIds);

        String requestBody = """
                {
                  "type": "file",
                  "files": [
                    { "name": "test.pdf", "path": "/upload/test.pdf", "extension": "pdf", "size": 1024 },
                    { "name": "notes.txt", "path": "/upload/notes.txt", "extension": "txt", "size": 512 }
                  ]
                }
                """;

        // Act & Assert
        mockMvc.perform(post("/console/v1/knowledge-bases/{kbId}/documents", kbId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                // /console/v1/** must use integer code (not string)
                .andExpect(jsonPath("$.code").isNumber())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0]").value("doc-id-1"))
                .andExpect(jsonPath("$.data[1]").value("doc-id-2"));
    }

    /**
     * P0-19: createDocuments — verifies the service is called with the correct kbId
     * extracted from the path variable (not from the request body).
     */
    @Test
    void createDocuments_setsKbIdFromPathVariable_onDelegatedRequest() throws Exception {
        // Arrange
        String kbId = "kb-from-path";
        when(documentService.createDocuments(any(CreateDocumentRequest.class)))
                .thenReturn(List.of("doc-abc"));

        String requestBody = """
                {
                  "type": "file",
                  "files": [
                    { "name": "report.pdf", "path": "/upload/report.pdf", "extension": "pdf", "size": 2048 }
                  ]
                }
                """;

        // Act
        mockMvc.perform(post("/console/v1/knowledge-bases/{kbId}/documents", kbId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        // Assert — the request passed to the service has kbId set from the path
        ArgumentCaptor<CreateDocumentRequest> captor = ArgumentCaptor.forClass(CreateDocumentRequest.class);
        verify(documentService).createDocuments(captor.capture());
        assertThat(captor.getValue().getKbId()).isEqualTo(kbId);
    }

    /**
     * P0-19: createDocuments — missing 'type' field should trigger BizException.
     * Validate the controller guard before delegating to the service.
     * Without a registered exception handler, MockMvc propagates the exception
     * as a nested ServletException — we assert the service is never called.
     */
    @Test
    void createDocuments_missingType_serviceNotInvoked() {
        // Arrange
        String kbId = "kb-123";
        String requestBody = """
                {
                  "files": [
                    { "name": "test.pdf", "path": "/upload/test.pdf", "extension": "pdf", "size": 1024 }
                  ]
                }
                """;

        // Act — BizException wraps in ServletException; just perform the request
        try {
            mockMvc.perform(post("/console/v1/knowledge-bases/{kbId}/documents", kbId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody));
        }
        catch (Exception ignored) {
            // BizException propagates — expected
        }

        // Assert — service must never be invoked when type is null
        org.mockito.Mockito.verifyNoInteractions(documentService);
    }

    /**
     * P0-19: createDocuments — empty 'files' list should block delegation.
     * The controller guard throws BizException before calling the service.
     */
    @Test
    void createDocuments_emptyFiles_serviceNotInvoked() {
        // Arrange
        String kbId = "kb-123";
        String requestBody = """
                {
                  "type": "file",
                  "files": []
                }
                """;

        // Act — empty files triggers BizException in controller guard
        try {
            mockMvc.perform(post("/console/v1/knowledge-bases/{kbId}/documents", kbId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody));
        }
        catch (Exception ignored) {
            // BizException propagates — expected
        }

        // Assert — service is never invoked for empty files
        org.mockito.Mockito.verifyNoInteractions(documentService);
    }

    /**
     * P0-19: Result wrapper — code field is always a number (integer), never a string.
     * This distinguishes /console/v1/** from /api/v1/apps/** which uses a string code.
     */
    @Test
    void createDocuments_resultCodeIsInteger_notString() throws Exception {
        // Arrange
        String kbId = "kb-integer-check";
        when(documentService.createDocuments(any(CreateDocumentRequest.class)))
                .thenReturn(List.of("doc-xyz"));

        String requestBody = """
                {
                  "type": "file",
                  "files": [
                    { "name": "a.pdf", "path": "/a.pdf", "extension": "pdf", "size": 100 }
                  ]
                }
                """;

        // Act & Assert — jsonPath isNumber() confirms integer type, not string
        mockMvc.perform(post("/console/v1/knowledge-bases/{kbId}/documents", kbId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").isNumber());
    }

}
