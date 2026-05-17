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

import com.alibaba.cloud.ai.studio.core.base.manager.RedisManager;
import com.alibaba.cloud.ai.studio.core.base.service.AppService;
import com.alibaba.cloud.ai.studio.core.context.RequestContextHolder;
import com.alibaba.cloud.ai.studio.core.workflow.WorkflowContext;
import com.alibaba.cloud.ai.studio.core.workflow.WorkflowInnerService;
import com.alibaba.cloud.ai.studio.core.workflow.runtime.WorkflowExecuteManager;
import com.alibaba.cloud.ai.studio.runtime.domain.RequestContext;
import com.alibaba.cloud.ai.studio.runtime.domain.app.ApplicationVersion;
import com.alibaba.cloud.ai.studio.runtime.domain.workflow.debug.TaskRunResponse;
import com.alibaba.cloud.ai.studio.runtime.exception.BizException;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for {@link WorkflowController} — P0-13 (Workflow debug stream / SSE).
 *
 * <p>Strategy:
 * <ul>
 *   <li>MockMvc standalone — no Spring context, no DB, no Redis.
 *   <li>All heavy collaborators ({@link WorkflowExecuteManager}, {@link RedisManager},
 *       {@link AppService}, {@link WorkflowInnerService}) are mocked.
 *   <li>SSE endpoint is verified to start async dispatch and return {@code text/event-stream};
 *       full event content is NOT asserted (requires a running graph-core runtime).
 *   <li>Debug lifecycle endpoints (run-task, stop-task) are verified to return integer
 *       {@code code=200} and delegate to the correct collaborator.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class WorkflowControllerTest {

	@Mock
	private RedisManager redisManager;

	@Mock
	private AppService appService;

	@Mock
	private WorkflowExecuteManager workflowExecuteManager;

	@Mock
	private WorkflowInnerService workflowInnerService;

	@InjectMocks
	private WorkflowController workflowController;

	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void setUp() {
		// Register Jackson converter so Result<T> is serialized as JSON, not toString().
		mockMvc = MockMvcBuilders.standaloneSetup(workflowController)
			.setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
			.build();

		// Pre-populate the request context thread-local so controller methods that call
		// RequestContextHolder.getRequestContext() get a non-null context instead of NPE.
		RequestContext ctx = new RequestContext();
		ctx.setRequestId("test-req-id");
		ctx.setWorkspaceId("ws-001");
		ctx.setAccountId("acct-001");
		RequestContextHolder.setRequestContext(ctx);
	}

	@AfterEach
	void tearDown() {
		RequestContextHolder.clearRequestContext();
	}

	// -----------------------------------------------------------------------
	// POST /console/v1/apps/workflow/debug/run-task
	// -----------------------------------------------------------------------

	@Test
	void runDebugTask_validRequest_returnsIntegerCode200() throws Exception {
		// Arrange
		ApplicationVersion appVersion = new ApplicationVersion();
		appVersion.setConfig("{}");
		when(appService.getAppVersion(anyString(), anyString())).thenReturn(appVersion);

		TaskRunResponse taskRunResponse = new TaskRunResponse();
		taskRunResponse.setTaskId("task-001");
		taskRunResponse.setConversationId("conv-001");
		taskRunResponse.setRequestId("test-req-id");
		when(workflowExecuteManager.runTask(any(), any(), any(), any())).thenReturn(taskRunResponse);

		String body = "{\"app_id\":\"app-001\",\"version\":\"latest\"}";

		// Act & Assert
		mockMvc.perform(post("/console/v1/apps/workflow/debug/run-task")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isOk())
			// /console/v1/** uses integer code (200 on success) per CLAUDE.md wrapper-split
			.andExpect(jsonPath("$.code").isNumber())
			.andExpect(jsonPath("$.code").value(200))
			.andExpect(jsonPath("$.data.task_id").value("task-001"));
	}

	@Test
	void runDebugTask_nullAppId_throwsBizException() {
		// Arrange — missing app_id; controller throws BizException before delegating.
		// In MockMvc standalone mode, an uncaught exception is wrapped in
		// NestedServletException and re-thrown from perform(), so we assert via
		// assertThatThrownBy rather than status().
		String body = "{\"version\":\"latest\"}";

		assertThatThrownBy(() ->
				mockMvc.perform(post("/console/v1/apps/workflow/debug/run-task")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
						.andReturn()
		).hasCauseInstanceOf(BizException.class);
	}

	@Test
	void runDebugTask_delegatesToWorkflowExecuteManager() throws Exception {
		// Arrange
		ApplicationVersion appVersion = new ApplicationVersion();
		appVersion.setConfig("{}");
		when(appService.getAppVersion(anyString(), anyString())).thenReturn(appVersion);

		TaskRunResponse taskRunResponse = new TaskRunResponse();
		taskRunResponse.setTaskId("task-002");
		when(workflowExecuteManager.runTask(any(), any(), any(), any())).thenReturn(taskRunResponse);

		String body = "{\"app_id\":\"app-002\"}";

		// Act
		mockMvc.perform(post("/console/v1/apps/workflow/debug/run-task")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isOk());

		// Assert delegation happened
		verify(workflowExecuteManager).runTask(any(ApplicationVersion.class), isNull(), isNull(),
				any(WorkflowContext.class));
	}

	// -----------------------------------------------------------------------
	// POST /console/v1/apps/workflow/debug/part-graph/stop-task
	// -----------------------------------------------------------------------

	@Test
	void stopTask_validTaskId_returnsTrueWithCode200() throws Exception {
		// Arrange
		when(workflowExecuteManager.stopTask(anyString())).thenReturn(Boolean.TRUE);

		String body = "{\"task_id\":\"task-stop-001\"}";

		// Act & Assert
		mockMvc.perform(post("/console/v1/apps/workflow/debug/part-graph/stop-task")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").isNumber())
			.andExpect(jsonPath("$.code").value(200))
			.andExpect(jsonPath("$.data").value(true));
	}

	@Test
	void stopTask_missingTaskId_returnsError() throws Exception {
		// Arrange — no task_id in body
		String body = "{}";

		// Act & Assert — controller returns Result.error (not a throw), so HTTP 200 but
		// non-success code
		mockMvc.perform(post("/console/v1/apps/workflow/debug/part-graph/stop-task")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isOk())
			// error path: code is non-200
			.andExpect(jsonPath("$.code").isNumber())
			.andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not(200)));
	}

	@Test
	void stopTask_delegatesToWorkflowExecuteManager() throws Exception {
		// Arrange
		when(workflowExecuteManager.stopTask("task-stop-002")).thenReturn(Boolean.FALSE);

		String body = "{\"task_id\":\"task-stop-002\"}";

		// Act
		mockMvc.perform(post("/console/v1/apps/workflow/debug/part-graph/stop-task")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isOk());

		// Assert
		verify(workflowExecuteManager).stopTask("task-stop-002");
	}

	// -----------------------------------------------------------------------
	// POST /console/v1/apps/workflow/{appId}/run_stream  — SSE endpoint (P0-13)
	// -----------------------------------------------------------------------

	/**
	 * Verifies that the SSE streaming endpoint produces an {@link SseEmitter} and
	 * responds with content-type {@code text/event-stream} and HTTP 200.
	 *
	 * <p>Because {@link WorkflowController#streamEvents} uses {@link ThreadPoolUtils}
	 * to offload the SSE loop onto a background thread, the async result (the emitter
	 * itself) is set synchronously by Spring MVC's async support before the background
	 * thread runs. We wait for the async result to be committed using
	 * {@link MockMvc#perform(javax.servlet.RequestDispatcher)} with
	 * {@code request().asyncStarted()} and then dispatch.
	 *
	 * <p>The full event stream body is NOT asserted — that would require a live
	 * graph-core runtime to produce events.
	 *
	 * <p>// TODO P0-13: A full e2e test requires a running graph-core runtime.
	 */
	@Test
	void runStream_returnsTextEventStream() throws Exception {
		// Arrange — stub app-lookup so the controller reaches the emitter-creation step.
		ApplicationVersion appVersion = new ApplicationVersion();
		appVersion.setConfig("{}");
		when(appService.getAppVersion(anyString(), anyString())).thenReturn(appVersion);

		// Background thread calls runTask; make it return a TaskRunResponse so the
		// inner loop proceeds. The loop will then try to get context from Redis (null)
		// which causes it to throw, triggering emitter.complete() via the finally block.
		TaskRunResponse taskRunResponse = new TaskRunResponse();
		taskRunResponse.setTaskId("stream-task-001");
		taskRunResponse.setConversationId("conv-stream-001");
		when(workflowExecuteManager.runTask(any(), any(), any(), any())).thenReturn(taskRunResponse);
		// redisManager.get returns null → innerRunStream throws BizException → emitter.complete()
		when(redisManager.get(anyString())).thenReturn(null);

		String body = "{\"conversation_id\":\"conv-stream-001\"}";

		// Act — perform request. Spring MVC sets async result = emitter synchronously.
		MvcResult asyncResult = mockMvc.perform(post("/console/v1/apps/workflow/app-001/run_stream")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(request().asyncStarted())
			.andReturn();

		// Wait for background thread to complete emitter, then dispatch and assert
		asyncResult.getAsyncResult(5000L);
		mockMvc.perform(asyncDispatch(asyncResult))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
	}

}
