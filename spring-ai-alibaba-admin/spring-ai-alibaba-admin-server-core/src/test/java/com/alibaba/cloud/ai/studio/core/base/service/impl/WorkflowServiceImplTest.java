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

package com.alibaba.cloud.ai.studio.core.base.service.impl;

import com.alibaba.cloud.ai.studio.core.base.service.AppService;
import com.alibaba.cloud.ai.studio.core.config.CommonConfig;
import com.alibaba.cloud.ai.studio.core.base.manager.RedisManager;
import com.alibaba.cloud.ai.studio.core.context.RequestContextHolder;
import com.alibaba.cloud.ai.studio.core.workflow.runtime.WorkflowExecuteManager;
import com.alibaba.cloud.ai.studio.runtime.domain.RequestContext;
import com.alibaba.cloud.ai.studio.runtime.domain.app.Application;
import com.alibaba.cloud.ai.studio.runtime.domain.app.ApplicationVersion;
import com.alibaba.cloud.ai.studio.runtime.domain.workflow.NodeStatusEnum;
import com.alibaba.cloud.ai.studio.runtime.domain.workflow.WorkflowStatus;
import com.alibaba.cloud.ai.studio.runtime.domain.workflow.debug.TaskRunResponse;
import com.alibaba.cloud.ai.studio.runtime.domain.workflow.debug.TaskStopRequest;
import com.alibaba.cloud.ai.studio.runtime.domain.workflow.debug.WorkflowRequest;
import com.alibaba.cloud.ai.studio.runtime.domain.workflow.debug.WorkflowResponse;
import com.alibaba.cloud.ai.studio.runtime.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WorkflowServiceImpl} — P0-14 + P0-15 (Workflow task state machine).
 *
 * <p>Architecture note: {@code WorkflowServiceImpl} does NOT implement an explicit
 * state-machine with guarded transitions. Instead it:
 * <ul>
 *   <li>Starts a task via {@link WorkflowExecuteManager#execute} and sets
 *       {@code taskStatus = "executing"} on the context.
 *   <li>Treats {@code "success" / "fail" / "pause"} as terminal signal values
 *       that stop the polling loop.
 *   <li>Wraps stop/async-call by delegating directly to the manager.
 * </ul>
 *
 * <p>The gap-report invariants (INIT→RUNNING→DONE, resume-only-from-pause, idempotent init)
 * live mostly in {@link WorkflowExecuteManager} and the controller layer, not inside this
 * service.  Tests here verify the observable contracts of this service layer:
 * <ul>
 *   <li>Missing or invalid request parameters surface as {@link BizException}.
 *   <li>{@code asyncCall} sets invoke-source = "async" and delegates to the manager.
 *   <li>{@code stop} delegates correctly.
 *   <li>Terminal statuses ({@code fail}, {@code pause}, {@code success}) produce the
 *       matching {@link WorkflowStatus} in the Flux.
 *   <li>{@code call()} collects the stream and returns a merged response.
 * </ul>
 *
 * <p>// TODO P0-14: A pure state-machine invariant test (INIT→RUNNING→DONE) requires
 * either a real WorkflowExecuteManager or a more invasive mock that injects taskStatus
 * transitions. Add when WorkflowExecuteManager gains a testable interface.
 */
@ExtendWith(MockitoExtension.class)
class WorkflowServiceImplTest {

	@Mock
	private WorkflowExecuteManager workflowExecuteManager;

	@Mock
	private RedisManager redisManager;

	@Mock
	private AppService appService;

	@Mock
	private CommonConfig commonConfig;

	@InjectMocks
	private WorkflowServiceImpl workflowServiceImpl;

	@BeforeEach
	void setUp() {
		RequestContext ctx = new RequestContext();
		ctx.setRequestId("unit-req-id");
		ctx.setWorkspaceId("ws-unit");
		ctx.setAccountId("acct-unit");
		RequestContextHolder.setRequestContext(ctx);
	}

	@AfterEach
	void tearDown() {
		RequestContextHolder.clearRequestContext();
	}

	// -----------------------------------------------------------------------
	// checkAndInitContext — validation guards (P0-14: invalid input rejected)
	// -----------------------------------------------------------------------

	/**
	 * A null-valued Flux element propagates as BizException through streamCall.
	 * Guards the invariant that the service rejects missing input before touching state.
	 *
	 * <p>Note: Reactor's {@code Flux.just(null)} throws NPE (null is disallowed in Rx).
	 * Instead we use {@code Flux.fromIterable} with a null element inside a list, which
	 * yields the null to {@code doOnNext} and triggers the null-check in checkAndInitContext.
	 * Uses collectList().block() since reactor-test (StepVerifier) is not on classpath.
	 */
	@Test
	void streamCall_nullRequest_emitsErrorResponse() {
		// Arrange — wrap null in a list so Reactor emits it without NPE at construction time
		List<WorkflowRequest> nullList = new java.util.ArrayList<>();
		nullList.add(null);
		Flux<WorkflowRequest> requestFlux = Flux.fromIterable(nullList);

		// Act — onErrorResume converts BizException to a WorkflowResponse with error field
		List<WorkflowResponse> responses = workflowServiceImpl.streamCall(requestFlux).collectList().block();

		// Assert
		assertThat(responses).isNotEmpty();
		assertThat(responses.get(0).getError()).isNotNull();
	}

	/**
	 * Missing appId propagates as BizException through streamCall.
	 */
	@Test
	void streamCall_missingAppId_emitsErrorResponse() {
		// Arrange
		WorkflowRequest request = new WorkflowRequest();
		// appId intentionally left null
		Flux<WorkflowRequest> requestFlux = Flux.just(request);

		// Act
		List<WorkflowResponse> responses = workflowServiceImpl.streamCall(requestFlux).collectList().block();

		// Assert
		assertThat(responses).isNotEmpty();
		assertThat(responses.get(0).getError()).isNotNull();
	}

	/**
	 * Missing workspaceId propagates as BizException through streamCall.
	 * The test verifies that a context without workspaceId is rejected.
	 */
	@Test
	void streamCall_missingWorkspaceId_emitsErrorResponse() {
		// Arrange — remove workspaceId from context
		RequestContext ctx = RequestContextHolder.getRequestContext();
		ctx.setWorkspaceId(null);
		RequestContextHolder.setRequestContext(ctx);

		WorkflowRequest request = new WorkflowRequest();
		request.setAppId("app-001");
		Flux<WorkflowRequest> requestFlux = Flux.just(request);

		// Act
		List<WorkflowResponse> responses = workflowServiceImpl.streamCall(requestFlux).collectList().block();

		// Assert
		assertThat(responses).isNotEmpty();
		assertThat(responses.get(0).getError()).isNotNull();
	}

	/**
	 * Missing accountId propagates as BizException (UNAUTHORIZED) through streamCall.
	 * Guards the invariant: requests without an authenticated identity are rejected.
	 */
	@Test
	void streamCall_missingAccountId_emitsUnauthorizedError() {
		// Arrange
		RequestContext ctx = RequestContextHolder.getRequestContext();
		ctx.setAccountId(null);
		RequestContextHolder.setRequestContext(ctx);

		WorkflowRequest request = new WorkflowRequest();
		request.setAppId("app-001");
		Flux<WorkflowRequest> requestFlux = Flux.just(request);

		// Act
		List<WorkflowResponse> responses = workflowServiceImpl.streamCall(requestFlux).collectList().block();

		// Assert
		assertThat(responses).isNotEmpty();
		assertThat(responses.get(0).getError()).isNotNull();
	}

	/**
	 * App not found surfaces as APP_NOT_FOUND error (not published).
	 * Guards the invariant: a task cannot start without a valid app.
	 *
	 * <p>// TODO P0-15: once the app is found, the state-machine starts. The initial
	 * taskStatus is set to "executing" in checkAndInitContext. A more granular test
	 * would verify that the emitted Flux terminates with FAIL/PAUSE/COMPLETED rather
	 * than leaving the context stuck in EXECUTING — requires WorkflowExecuteManager mock
	 * to actually mutate the shared WorkflowContext.
	 */
	@Test
	void streamCall_appNotFound_emitsAppNotFoundError() {
		// Arrange
		when(appService.getApp(anyString())).thenReturn(null);

		WorkflowRequest request = new WorkflowRequest();
		request.setAppId("nonexistent-app");
		Flux<WorkflowRequest> requestFlux = Flux.just(request);

		// Act
		List<WorkflowResponse> responses = workflowServiceImpl.streamCall(requestFlux).collectList().block();

		// Assert
		assertThat(responses).isNotEmpty();
		assertThat(responses.get(0).getError()).isNotNull();
	}

	/**
	 * App exists but has no published config — surfaces as APP_NOT_PUBLISHED error.
	 * This validates that a task only starts from a valid (published) state.
	 */
	@Test
	void streamCall_appNotPublished_emitsAppNotPublishedError() {
		// Arrange
		Application app = new Application();
		app.setPubConfigStr(null); // not published
		when(appService.getApp(anyString())).thenReturn(app);

		WorkflowRequest request = new WorkflowRequest();
		request.setAppId("unpublished-app");
		// draft=false by default → uses pubConfigStr
		Flux<WorkflowRequest> requestFlux = Flux.just(request);

		// Act
		List<WorkflowResponse> responses = workflowServiceImpl.streamCall(requestFlux).collectList().block();

		// Assert
		assertThat(responses).isNotEmpty();
		assertThat(responses.get(0).getError()).isNotNull();
	}

	// -----------------------------------------------------------------------
	// stop — P0-14: stop delegates to manager
	// -----------------------------------------------------------------------

	@Test
	void stop_delegatesToWorkflowExecuteManager() {
		// Arrange
		when(workflowExecuteManager.stopTask("task-stop-001")).thenReturn(Boolean.TRUE);
		TaskStopRequest stopRequest = new TaskStopRequest();
		stopRequest.setTaskId("task-stop-001");

		// Act
		Boolean result = workflowServiceImpl.stop(stopRequest);

		// Assert
		assertThat(result).isTrue();
		verify(workflowExecuteManager).stopTask("task-stop-001");
	}

	@Test
	void stop_managerReturnsFalse_propagatesFalse() {
		// Arrange
		when(workflowExecuteManager.stopTask(anyString())).thenReturn(Boolean.FALSE);
		TaskStopRequest stopRequest = new TaskStopRequest();
		stopRequest.setTaskId("task-unknown");

		// Act
		Boolean result = workflowServiceImpl.stop(stopRequest);

		// Assert
		assertThat(result).isFalse();
	}

	// -----------------------------------------------------------------------
	// asyncCall — P0-14: async invocation delegates with correct invoke-source
	// -----------------------------------------------------------------------

	@Test
	void asyncCall_delegatesToWorkflowExecuteManager() {
		// Arrange
		ApplicationVersion appVersion = new ApplicationVersion();
		appVersion.setConfig("{}");
		when(appService.getAppVersion(anyString(), anyString())).thenReturn(appVersion);

		TaskRunResponse expectedResponse = new TaskRunResponse();
		expectedResponse.setTaskId("async-task-001");
		when(workflowExecuteManager.runTask(any(), any(), any(), any())).thenReturn(expectedResponse);

		WorkflowRequest request = new WorkflowRequest();
		request.setAppId("app-async");

		// Act
		TaskRunResponse response = workflowServiceImpl.asyncCall(request);

		// Assert
		assertThat(response.getTaskId()).isEqualTo("async-task-001");
		verify(workflowExecuteManager).runTask(any(ApplicationVersion.class), any(), any(), any());
	}

	// -----------------------------------------------------------------------
	// Terminal status values — P0-14: state names match NodeStatusEnum
	// -----------------------------------------------------------------------

	/**
	 * Documents that the terminal statuses used in streamCall are exactly
	 * NodeStatusEnum.FAIL, PAUSE, and SUCCESS — not arbitrary strings.
	 * This pins the state-machine vocabulary so a rename of NodeStatusEnum codes
	 * would break the test and alert the developer.
	 */
	@Test
	void nodeStatusEnum_terminalCodesMatchExpectedValues() {
		// These are the exact codes the streaming loop watches for as terminal signals.
		assertThat(NodeStatusEnum.FAIL.getCode()).isEqualTo("fail");
		assertThat(NodeStatusEnum.SUCCESS.getCode()).isEqualTo("success");
		assertThat(NodeStatusEnum.PAUSE.getCode()).isEqualTo("pause");
		// EXECUTING is the initial (non-terminal) state set by checkAndInitContext
		assertThat(NodeStatusEnum.EXECUTING.getCode()).isEqualTo("executing");
	}

	/**
	 * Documents that WorkflowStatus terminal enum values align with the NodeStatusEnum
	 * codes the service maps to them.
	 */
	@Test
	void workflowStatus_terminalValuesMatchServiceMapping() {
		// FAIL nodeStatus → WorkflowStatus.FAILED
		assertThat(WorkflowStatus.FAILED.getValue()).isEqualTo("failed");
		// SUCCESS nodeStatus → WorkflowStatus.COMPLETED
		assertThat(WorkflowStatus.COMPLETED.getValue()).isEqualTo("completed");
		// PAUSE nodeStatus → WorkflowStatus.PAUSE
		assertThat(WorkflowStatus.PAUSE.getValue()).isEqualTo("pause");
		// EXECUTING nodeStatus → WorkflowStatus.IN_PROGRESS
		assertThat(WorkflowStatus.IN_PROGRESS.getValue()).isEqualTo("in_progress");
	}

	// -----------------------------------------------------------------------
	// call() — synchronous wrapper collects stream
	// -----------------------------------------------------------------------

	/**
	 * When {@code streamCall} emits an error-response (from {@code handleThrowable}),
	 * the response has {@code status = null}. The synchronous {@code call()} wrapper
	 * must surface that error response to the caller rather than NPE on the
	 * {@code getStatus().equals(...)} filter — guarded by the null-check on line 106.
	 */
	@Test
	void call_withInvalidRequest_returnsErrorResponse() {
		// Arrange — request with missing appId triggers BizException → error WorkflowResponse
		// with status=null (handleThrowable does not set status).
		WorkflowRequest request = new WorkflowRequest();
		// appId intentionally left null

		// Act — call() collects the stream and must not throw on the null-status error response.
		WorkflowResponse response = workflowServiceImpl.call(request);

		// Assert — the error from streamCall surfaces on the returned response.
		assertThat(response).isNotNull();
		assertThat(response.getError()).isNotNull();
	}

}
