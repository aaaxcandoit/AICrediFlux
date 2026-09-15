package aicrediflux.token.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import aicrediflux.token.agent.domain.AgentMessage;
import aicrediflux.token.agent.domain.AgentRun;
import aicrediflux.token.agent.mapper.AgentMessageMapper;
import aicrediflux.token.agent.mapper.AgentRunMapper;

class AgentRunExecutorTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-10T03:00:00Z"), ZoneOffset.UTC);

    @Test
    void completedChatRunStoresAssistantMessageAndUsageThroughSpringAiChatModel() {
        AgentRunMapper runMapper = org.mockito.Mockito.mock(AgentRunMapper.class);
        AgentMessageMapper messageMapper = org.mockito.Mockito.mock(AgentMessageMapper.class);
        InternalChatService internalChatService = org.mockito.Mockito.mock(InternalChatService.class);
        AicrediFluxCopilotToolCallbackProvider toolCallbackProvider = org.mockito.Mockito.mock(AicrediFluxCopilotToolCallbackProvider.class);
        AgentRunEventPublisher eventPublisher = org.mockito.Mockito.mock(AgentRunEventPublisher.class);
        AicrediFluxChatUsageHolder usageHolder = new AicrediFluxChatUsageHolder();
        AicrediFluxGatewayChatModel chatModel = new AicrediFluxGatewayChatModel(internalChatService, usageHolder);
        AgentRun run = runningRun();

        when(runMapper.selectByRunNo("AR001")).thenReturn(run);
        when(messageMapper.selectBySessionNo("AS001")).thenReturn(List.of(userMessage("帮我分析余额")));
        when(toolCallbackProvider.callbacks()).thenReturn(List.of());
        when(internalChatService.chat(any(InternalChatRequest.class))).thenReturn(
                new InternalChatResult("你的余额足够继续使用。", 12, 8, 20L, "req-agent-1", 3));
        when(messageMapper.insert(any(AgentMessage.class))).thenReturn(1);
        when(runMapper.completeSuccess("AR001", 12, 8, 20L, CLOCK.instant())).thenReturn(1);

        AgentRunExecutor executor = new AgentRunExecutor(runMapper, messageMapper, chatModel,
                toolCallbackProvider, usageHolder, eventPublisher, CLOCK);
        executor.execute("AR001");

        ArgumentCaptor<InternalChatRequest> requestCaptor = ArgumentCaptor.forClass(InternalChatRequest.class);
        verify(internalChatService).chat(requestCaptor.capture());
        assertThat(requestCaptor.getValue().userId()).isEqualTo(7);
        assertThat(requestCaptor.getValue().model()).isEqualTo("qwen-plus");
        assertThat(requestCaptor.getValue().messages())
                .extracting(InternalChatMessage::content)
                .containsExactly("帮我分析余额");

        ArgumentCaptor<AgentMessage> messageCaptor = ArgumentCaptor.forClass(AgentMessage.class);
        verify(messageMapper).insert(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getRole()).isEqualTo(AgentMessage.ROLE_ASSISTANT);
        assertThat(messageCaptor.getValue().getContent()).isEqualTo("你的余额足够继续使用。");
        assertThat(messageCaptor.getValue().getUsageJson()).contains("\"quota\":20");
        verify(runMapper).completeSuccess("AR001", 12, 8, 20L, CLOCK.instant());
        verify(eventPublisher).textDelta("AR001", "你的余额足够继续使用。");
        verify(eventPublisher).usage("AR001", 12, 8, 20L);
        verify(eventPublisher).done("AR001", AgentRun.STATUS_COMPLETED);
    }

    @Test
    void runStatusModelExposesPhaseTwoStates() {
        assertThat(AgentRun.TERMINAL_STATUSES).containsExactlyInAnyOrder(
                AgentRun.STATUS_COMPLETED, AgentRun.STATUS_FAILED, AgentRun.STATUS_CANCELLED);
        assertThat(AgentRun.ACTIVE_STATUSES).containsExactlyInAnyOrder(
                AgentRun.STATUS_CREATED, AgentRun.STATUS_RUNNING, AgentRun.STATUS_WAITING_TOOL);
    }

    private AgentRun runningRun() {
        AgentRun run = new AgentRun();
        run.setRunNo("AR001");
        run.setSessionNo("AS001");
        run.setUserId(7);
        run.setModel("qwen-plus");
        run.setStatus(AgentRun.STATUS_RUNNING);
        run.setRequestId("req-agent-1");
        run.setSource("AGENT");
        return run;
    }

    private AgentMessage userMessage(String content) {
        AgentMessage message = new AgentMessage();
        message.setMessageNo("AM001");
        message.setSessionNo("AS001");
        message.setUserId(7);
        message.setRole(AgentMessage.ROLE_USER);
        message.setContent(content);
        message.setCreateTime(CLOCK.instant());
        return message;
    }
}
