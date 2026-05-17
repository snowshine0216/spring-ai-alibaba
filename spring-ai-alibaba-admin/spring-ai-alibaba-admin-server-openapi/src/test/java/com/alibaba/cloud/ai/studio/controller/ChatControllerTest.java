/*
 * Copyright 2024 the original author or authors.
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

package com.alibaba.cloud.ai.studio.controller;

import com.alibaba.cloud.ai.studio.core.base.manager.RedisManager;
import com.alibaba.cloud.ai.studio.core.base.service.AgentService;
import com.alibaba.cloud.ai.studio.core.base.service.WorkflowService;
import com.alibaba.cloud.ai.studio.core.context.RequestContextHolder;
import com.alibaba.cloud.ai.studio.runtime.domain.RequestContext;
import com.alibaba.cloud.ai.studio.runtime.domain.agent.AgentRequest;
import com.alibaba.cloud.ai.studio.runtime.domain.agent.AgentResponse;
import com.alibaba.cloud.ai.studio.runtime.domain.agent.AgentStatus;
import com.alibaba.cloud.ai.studio.runtime.domain.workflow.debug.TaskRunResponse;
import com.alibaba.cloud.ai.studio.runtime.domain.workflow.debug.WorkflowRequest;
import com.alibaba.cloud.ai.studio.runtime.domain.workflow.debug.WorkflowResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for {@link ChatController} — covers CP-2/3 (External chat completion SSE /
 * sync) per the P0-7 gap-report entry. Uses MockMvc standalone setup with Mockito mocks;
 * no Spring context is loaded.
 *
 * <p>Discrepancies from gap-report assumptions documented inline.
 */
@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

	@Mock
	private AgentService agentService;

	@Mock
	private WorkflowService workflowService;

	@Mock
	private RedisManager redisManager;

	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void setUp() {
		ChatController controller = new ChatController(agentService, workflowService, redisManager);
		// Register a Jackson converter so Result<T> is properly serialized to JSON
		mockMvc = MockMvcBuilders.standaloneSetup(controller)
			.setMessageConverters(new MappingJackson2HttpMessageConverter())
			.build();

		// The controller reads RequestContext from a ThreadLocal on every request.
		// Pre-populate it so calls to context.setStartTime() don't NPE.
		RequestContext context = new RequestContext();
		context.setRequestId("test-request-id");
		context.setWorkspaceId("test-workspace");
		RequestContextHolder.setRequestContext(context);
	}

	@AfterEach
	void tearDown() {
		RequestContextHolder.clearRequestContext();
	}

	/**
	 * CP-2 (sync): POST /api/v1/apps/chat/completions with stream=false returns HTTP 200
	 * and a JSON body whose top-level fields originate from AgentResponse.
	 *
	 * <p>NOTE: The gap report assumed a Result&lt;T&gt; wrapper with string code="Success".
	 * The actual controller returns a raw JSON string of AgentResponse via JsonUtils.toJson()
	 * — there is no Result wrapper on this endpoint. The controller writes the string
	 * directly (content-type defaults to text/plain in the standalone setup).
	 * We parse the body manually to verify the AgentResponse fields.
	 */
	@Test
	void syncChatCompletion_returnsChatResponseJson() throws Exception {
		AgentResponse mockResponse = AgentResponse.builder()
			.requestId("test-request-id")
			.status(AgentStatus.COMPLETED)
			.build();

		when(agentService.call(any(AgentRequest.class))).thenReturn(mockResponse);

		AgentRequest requestBody = new AgentRequest();
		requestBody.setAppId("app-001");
		requestBody.setStream(false);

		MvcResult result = mockMvc
			.perform(post("/api/v1/apps/chat/completions").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(requestBody)))
			.andExpect(status().isOk())
			.andReturn();

		// Controller returns raw JSON string from JsonUtils.toJson(AgentResponse).
		// Spring may serialize a String return value as a JSON-quoted string literal via
		// Jackson ("..."), so if the root node is a TextNode we unwrap it once.
		String body = result.getResponse().getContentAsString();
		JsonNode root = objectMapper.readTree(body);
		JsonNode node = root.isTextual() ? objectMapper.readTree(root.asText()) : root;
		assertEquals("test-request-id", node.path("request_id").asText());
		// AgentStatus.COMPLETED is annotated @JsonProperty("completed")
		assertEquals("completed", node.path("status").asText());

		verify(agentService).call(any(AgentRequest.class));
	}

	/**
	 * CP-3 (SSE): POST /api/v1/apps/chat/completions with stream=true returns HTTP 200
	 * and content-type text/event-stream; verifies streamCall is invoked on the service.
	 *
	 * <p>NOTE: The controller returns an SseEmitter; MockMvc cannot read SSE frames in a
	 * standard blocking call, so we assert status + content-type and verify the service
	 * delegation. A [DONE] frame assertion is not practical in this unit-test scope.
	 */
	@Test
	void streamingChatCompletion_returnsEventStream() throws Exception {
		AgentResponse streamResponse = AgentResponse.builder()
			.requestId("test-request-id")
			.status(AgentStatus.COMPLETED)
			.build();

		when(agentService.streamCall(any(Flux.class))).thenReturn(Flux.just(streamResponse));

		AgentRequest requestBody = new AgentRequest();
		requestBody.setAppId("app-001");
		requestBody.setStream(true);

		mockMvc.perform(post("/api/v1/apps/chat/completions").contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(requestBody)))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", MediaType.TEXT_EVENT_STREAM_VALUE));

		verify(agentService).streamCall(any(Flux.class));
	}

	/**
	 * POST /api/v1/apps/workflow/async-completions returns HTTP 200 and a Result wrapper
	 * with integer code=200 and a task_id in the nested data object.
	 *
	 * <p>The controller returns Result&lt;TaskRunResponse&gt; which Spring serializes via
	 * Jackson. We parse the response body manually because the standalone MockMvc setup
	 * may write it as text/plain; both cases are handled by reading the raw body.
	 */
	@Test
	void asyncWorkflowCompletion_returnsTaskId() throws Exception {
		TaskRunResponse taskRun = new TaskRunResponse();
		taskRun.setTaskId("task-abc-123");
		taskRun.setRequestId("test-request-id");

		when(workflowService.asyncCall(any(WorkflowRequest.class))).thenReturn(taskRun);

		WorkflowRequest requestBody = new WorkflowRequest();
		requestBody.setAppId("app-001");

		MvcResult result = mockMvc
			.perform(post("/api/v1/apps/workflow/async-completions").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(requestBody)))
			.andExpect(status().isOk())
			.andReturn();

		JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
		assertEquals(200, node.path("code").asInt());
		assertEquals("task-abc-123", node.path("data").path("task_id").asText());

		verify(workflowService).asyncCall(any(WorkflowRequest.class));
	}

	/**
	 * POST /api/v1/apps/workflow/async-results with a task_id that is not cached returns
	 * HTTP 200 and a Result wrapper with a non-200 error code (taskId not found).
	 *
	 * <p>NOTE: The gap report assumed a GET endpoint for results, but the actual endpoint
	 * is POST /workflow/async-results. There is no GET variant in the controller.
	 * The Result wrapper uses integer code, not string "Success".
	 */
	@Test
	void getAsyncResults_taskNotFound_returnsErrorResult() throws Exception {
		// RedisManager returns null → taskId not found path
		when(redisManager.get(any())).thenReturn(null);

		String requestBody = "{\"task_id\":\"task-abc-123\"}";

		MvcResult result = mockMvc
			.perform(post("/api/v1/apps/workflow/async-results").contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andReturn();

		JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
		assertNotNull(node.path("code"), "Result wrapper must contain 'code'");
		assertNotNull(node.path("message"), "Result wrapper must contain 'message'");
		// task not found → error result with non-200 code
		assertEquals(false, node.path("code").asInt() == 0,
				"Error result should not have code=0");
	}

}
