package aicrediflux.token.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
import aicrediflux.token.rag.service.RagCitation;
import aicrediflux.token.rag.service.RagRetrievalResult;
import aicrediflux.token.rag.service.RagRetrievalService;

class AgentRunExecutorRagTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-12T02:00:00Z"), ZoneOffset.UTC);

    @Test
    void injectsRagReferencesIntoPromptAndPublishesSseReferences() {
        AgentRunMapper runMapper = org.mockito.Mockito.mock(AgentRunMapper.class);
        AgentMessageMapper messageMapper = org.mockito.Mockito.mock(AgentMessageMapper.class);
        InternalChatService internalChatService = org.mockito.Mockito.mock(InternalChatService.class);
        AicrediFluxCopilotToolCallbackProvider toolCallbackProvider = org.mockito.Mockito.mock(AicrediFluxCopilotToolCallbackProvider.class);
        AgentRunEventPublisher eventPublisher = org.mockito.Mockito.mock(AgentRunEventPublisher.class);
        RagRetrievalService ragRetrievalService = org.mockito.Mockito.mock(RagRetrievalService.class);
        AicrediFluxChatUsageHolder usageHolder = new AicrediFluxChatUsageHolder();
        AicrediFluxGatewayChatModel chatModel = new AicrediFluxGatewayChatModel(internalChatService, usageHolder);
        AgentRun run = runningRun();
        RagRetrievalResult ragResult = RagRetrievalResult.available(List.of(
                new RagCitation("RC1", "SiliconFlow 接入说明", "platform_docs", "DOC001", "siliconflow-onboarding", "platform_docs__siliconflow-onboarding.md", "SiliconFlow 接入", "Base URL 配置为上游兼容地址。", 0.91)
        ));

        when(runMapper.selectByRunNo("AR-RAG")).thenReturn(run);
        when(messageMapper.selectBySessionNo("AS-RAG")).thenReturn(List.of(userMessage("怎么接 SiliconFlow？")));
        when(toolCallbackProvider.callbacksFor(any(AicrediFluxToolInvocationContext.class), eq("怎么接 SiliconFlow？"))).thenReturn(List.of());
        when(ragRetrievalService.retrieveForRun(run, "怎么接 SiliconFlow？")).thenReturn(ragResult);
        when(internalChatService.chat(any(InternalChatRequest.class))).thenReturn(
                new InternalChatResult("按引用配置 SiliconFlow。", 20, 10, 30L, "req-rag", 2));
        when(messageMapper.insert(any(AgentMessage.class))).thenReturn(1);
        when(runMapper.completeSuccess("AR-RAG", 20, 10, 30L, CLOCK.instant())).thenReturn(1);

        AgentRunExecutor executor = new AgentRunExecutor(runMapper, messageMapper, chatModel,
                toolCallbackProvider, usageHolder, eventPublisher, ragRetrievalService, CLOCK);
        executor.execute("AR-RAG");

        ArgumentCaptor<InternalChatRequest> requestCaptor = ArgumentCaptor.forClass(InternalChatRequest.class);
        verify(internalChatService).chat(requestCaptor.capture());
        assertThat(requestCaptor.getValue().messages().get(0).role()).isEqualTo("system");
        assertThat(requestCaptor.getValue().messages().get(0).content())
                .contains("RAG 引用资料")
                .contains("SiliconFlow 接入说明")
                .contains("Base URL");
        verify(eventPublisher).ragRefs("AR-RAG", ragResult);
        verify(toolCallbackProvider).callbacksFor(any(AicrediFluxToolInvocationContext.class), eq("怎么接 SiliconFlow？"));
        verify(toolCallbackProvider, never()).callbacks();
    }

    private AgentRun runningRun() {
        AgentRun run = new AgentRun();
        run.setRunNo("AR-RAG");
        run.setSessionNo("AS-RAG");
        run.setUserId(7);
        run.setModel("deepseek-chat");
        run.setStatus(AgentRun.STATUS_RUNNING);
        run.setRequestId("req-rag");
        run.setSource("AGENT");
        return run;
    }

    private AgentMessage userMessage(String content) {
        AgentMessage message = new AgentMessage();
        message.setMessageNo("AM-RAG");
        message.setSessionNo("AS-RAG");
        message.setUserId(7);
        message.setRole(AgentMessage.ROLE_USER);
        message.setContent(content);
        message.setCreateTime(CLOCK.instant());
        return message;
    }
}

