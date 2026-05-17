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

import com.alibaba.cloud.ai.studio.core.base.service.AppService;
import com.alibaba.cloud.ai.studio.core.context.RequestContextHolder;
import com.alibaba.cloud.ai.studio.core.workflow.runtime.WorkflowExecuteManager;
import com.alibaba.cloud.ai.studio.runtime.domain.RequestContext;
import com.alibaba.cloud.ai.studio.runtime.domain.app.Application;
import com.alibaba.cloud.ai.studio.runtime.enums.AppType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for {@link AppController} — P0-8 (CP-4 Application publish).
 *
 * <p>Verifies that {@code POST /console/v1/apps/{appId}/publish} returns the
 * {@code Result} wrapper with integer {@code code=200}, delegates to
 * {@link AppService#publishApp(String)}, and that the service method is called
 * with the correct {@code appId}.
 *
 * <p>Uses MockMvc standalone setup; no Spring context is started.
 */
@ExtendWith(MockitoExtension.class)
class AppControllerTest {

    private static final String APP_ID = "app-001";

    @Mock
    private AppService appService;

    @Mock
    private WorkflowExecuteManager workflowExecuteManager;

    @InjectMocks
    private AppController appController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(appController)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                .build();

        // Set up request context (ThreadLocal) so the controller's
        // RequestContextHolder.getRequestContext() call succeeds.
        RequestContext ctx = new RequestContext();
        ctx.setRequestId("req-001");
        ctx.setWorkspaceId("ws-001");
        ctx.setAccountId("acc-001");
        RequestContextHolder.setRequestContext(ctx);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clearRequestContext();
    }

    /**
     * P0-8: publish a BASIC-type app — happy path.
     *
     * <p>Assertions:
     * <ul>
     *   <li>HTTP 200</li>
     *   <li>{@code $.code} is an integer with value 200 (console/v1 wrapper)</li>
     *   <li>{@link AppService#publishApp(String)} is called with the right appId</li>
     * </ul>
     */
    @Test
    void publishApp_basicType_returnsIntegerCode200AndDelegatesToService() throws Exception {
        // Arrange — getApp returns a BASIC application, publishApp is a no-op
        Application app = new Application();
        app.setAppId(APP_ID);
        app.setType(AppType.BASIC);
        when(appService.getApp(eq(APP_ID))).thenReturn(app);
        doNothing().when(appService).publishApp(eq(APP_ID));

        // Act & Assert
        mockMvc.perform(post("/console/v1/apps/{appId}/publish", APP_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // console/v1 controllers use integer code (200 on success)
                .andExpect(jsonPath("$.code").isNumber())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"));

        // Verify service delegation
        verify(appService).publishApp(APP_ID);
    }

    /**
     * P0-8: publish delegate — verify service method is invoked with the exact appId.
     *
     * <p>Ensures that the controller forwards the path variable unchanged.
     */
    @Test
    void publishApp_delegatesExactAppIdToService() throws Exception {
        String specificAppId = "specific-app-xyz";

        Application app = new Application();
        app.setAppId(specificAppId);
        app.setType(AppType.BASIC);
        when(appService.getApp(eq(specificAppId))).thenReturn(app);
        doNothing().when(appService).publishApp(eq(specificAppId));

        mockMvc.perform(post("/console/v1/apps/{appId}/publish", specificAppId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // The controller must pass the path variable to the service unchanged
        verify(appService).publishApp(specificAppId);
    }

    /**
     * P0-8: verify the response shape — data field is null/absent for a void publish.
     */
    @Test
    void publishApp_responseShape_dataIsNull() throws Exception {
        Application app = new Application();
        app.setAppId(APP_ID);
        app.setType(AppType.BASIC);
        when(appService.getApp(eq(APP_ID))).thenReturn(app);
        doNothing().when(appService).publishApp(eq(APP_ID));

        mockMvc.perform(post("/console/v1/apps/{appId}/publish", APP_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                // publish returns Result<Void>, so data should be null
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
