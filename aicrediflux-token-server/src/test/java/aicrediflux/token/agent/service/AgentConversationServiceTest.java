package aicrediflux.token.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import aicrediflux.token.agent.domain.AgentMessage;
import aicrediflux.token.agent.domain.AgentRun;
import aicrediflux.token.agent.domain.AgentSession;
import aicrediflux.token.agent.mapper.AgentMessageMapper;
import aicrediflux.token.agent.mapper.AgentRunMapper;
import aicrediflux.token.agent.mapper.AgentSessionMapper;

class AgentConversationServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-10T02:00:00Z"), ZoneOffset.UTC);

    @Test
    void createSessionStoresOwnedActiveSession() {
        AgentSessionMapper sessionMapper = org.mockito.Mockito.mock(AgentSessionMapper.class);
        AgentMessageMapper messageMapper = org.mockito.Mockito.mock(AgentMessageMapper.class);
        AgentRunMapper runMapper = org.mockito.Mockito.mock(AgentRunMapper.class);
        when(sessionMapper.insert(any(AgentSession.class))).thenReturn(1);

        AgentConversationService service = new AgentConversationService(sessionMapper, messageMapper, runMapper, CLOCK);
        AgentSession session = service.createSession(9, "  成本分析  ", "qwen-plus");

        ArgumentCaptor<AgentSession> captor = ArgumentCaptor.forClass(AgentSession.class);
        verify(sessionMapper).insert(captor.capture());
        assertThat(session.getSessionNo()).startsWith("AS");
        assertThat(captor.getValue().getUserId()).isEqualTo(9);
        assertThat(captor.getValue().getTitle()).isEqualTo("成本分析");
        assertThat(captor.getValue().getModel()).isEqualTo("qwen-plus");
        assertThat(captor.getValue().getStatus()).isEqualTo(AgentSession.STATUS_ACTIVE);
    }

    @Test
    void submitMessageCreatesUserMessageAndRunningRun() {
        AgentSessionMapper sessionMapper = org.mockito.Mockito.mock(AgentSessionMapper.class);
        AgentMessageMapper messageMapper = org.mockito.Mockito.mock(AgentMessageMapper.class);
        AgentRunMapper runMapper = org.mockito.Mockito.mock(AgentRunMapper.class);
        AgentSession session = ownedSession(7);
        when(sessionMapper.selectOwned("AS001", 7)).thenReturn(session);
        when(runMapper.selectRunningBySessionNo("AS001")).thenReturn(null);
        when(messageMapper.insert(any(AgentMessage.class))).thenReturn(1);
        when(runMapper.insert(any(AgentRun.class))).thenReturn(1);

        AgentConversationService service = new AgentConversationService(sessionMapper, messageMapper, runMapper, CLOCK);
        AgentRunSubmission submission = service.submitMessage(7, "AS001", "deepseek-chat", "帮我分析本周消耗");

        ArgumentCaptor<AgentMessage> messageCaptor = ArgumentCaptor.forClass(AgentMessage.class);
        ArgumentCaptor<AgentRun> runCaptor = ArgumentCaptor.forClass(AgentRun.class);
        verify(messageMapper).insert(messageCaptor.capture());
        verify(runMapper).insert(runCaptor.capture());
        assertThat(submission.runNo()).startsWith("AR");
        assertThat(messageCaptor.getValue().getRole()).isEqualTo(AgentMessage.ROLE_USER);
        assertThat(messageCaptor.getValue().getContent()).isEqualTo("帮我分析本周消耗");
        assertThat(runCaptor.getValue().getStatus()).isEqualTo(AgentRun.STATUS_RUNNING);
        assertThat(runCaptor.getValue().getSource()).isEqualTo("AGENT");
    }

    @Test
    void sameSessionRunningRunIsRejected() {
        AgentSessionMapper sessionMapper = org.mockito.Mockito.mock(AgentSessionMapper.class);
        AgentMessageMapper messageMapper = org.mockito.Mockito.mock(AgentMessageMapper.class);
        AgentRunMapper runMapper = org.mockito.Mockito.mock(AgentRunMapper.class);
        when(sessionMapper.selectOwned("AS001", 7)).thenReturn(ownedSession(7));
        AgentRun running = new AgentRun();
        running.setRunNo("AR-running");
        when(runMapper.selectRunningBySessionNo("AS001")).thenReturn(running);

        AgentConversationService service = new AgentConversationService(sessionMapper, messageMapper, runMapper, CLOCK);
        assertThrows(IllegalStateException.class, () -> service.submitMessage(7, "AS001", "qwen-plus", "hello"));

        verifyNoInteractions(messageMapper);
        verify(runMapper, never()).insert(any(AgentRun.class));
    }

    @Test
    void crossUserSessionAccessIsRejected() {
        AgentSessionMapper sessionMapper = org.mockito.Mockito.mock(AgentSessionMapper.class);
        AgentMessageMapper messageMapper = org.mockito.Mockito.mock(AgentMessageMapper.class);
        AgentRunMapper runMapper = org.mockito.Mockito.mock(AgentRunMapper.class);
        when(sessionMapper.selectOwned("AS001", 8)).thenReturn(null);

        AgentConversationService service = new AgentConversationService(sessionMapper, messageMapper, runMapper, CLOCK);
        assertThrows(IllegalArgumentException.class, () -> service.listMessages(8, "AS001"));

        verifyNoInteractions(messageMapper);
    }

    @Test
    void springContextCanCreateConversationServiceWithMapperConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(AgentSessionMapper.class, () -> org.mockito.Mockito.mock(AgentSessionMapper.class));
            context.registerBean(AgentMessageMapper.class, () -> org.mockito.Mockito.mock(AgentMessageMapper.class));
            context.registerBean(AgentRunMapper.class, () -> org.mockito.Mockito.mock(AgentRunMapper.class));
            context.register(AgentConversationService.class);

            context.refresh();

            assertThat(context.getBean(AgentConversationService.class)).isNotNull();
        }
    }

    @Test
    void renameSessionUpdatesOwnedSessionTitle() {
        AgentSessionMapper sessionMapper = org.mockito.Mockito.mock(AgentSessionMapper.class);
        AgentMessageMapper messageMapper = org.mockito.Mockito.mock(AgentMessageMapper.class);
        AgentRunMapper runMapper = org.mockito.Mockito.mock(AgentRunMapper.class);
        when(sessionMapper.selectOwned("AS001", 7)).thenReturn(ownedSession(7));
        when(sessionMapper.updateTitle("AS001", 7, "成本复盘", CLOCK.instant())).thenReturn(1);

        AgentConversationService service = new AgentConversationService(sessionMapper, messageMapper, runMapper, CLOCK);
        AgentSession session = service.renameSession(7, "AS001", "  成本复盘  ");

        assertThat(session.getTitle()).isEqualTo("成本复盘");
        verify(sessionMapper).updateTitle("AS001", 7, "成本复盘", CLOCK.instant());
    }

    @Test
    void renameSessionRejectsBlankTitle() {
        AgentSessionMapper sessionMapper = org.mockito.Mockito.mock(AgentSessionMapper.class);
        AgentMessageMapper messageMapper = org.mockito.Mockito.mock(AgentMessageMapper.class);
        AgentRunMapper runMapper = org.mockito.Mockito.mock(AgentRunMapper.class);
        when(sessionMapper.selectOwned("AS001", 7)).thenReturn(ownedSession(7));

        AgentConversationService service = new AgentConversationService(sessionMapper, messageMapper, runMapper, CLOCK);

        assertThrows(IllegalArgumentException.class, () -> service.renameSession(7, "AS001", "   "));
        verify(sessionMapper, never()).updateTitle(any(), any(Integer.class), any(), any());
    }

    @Test
    void deleteSessionArchivesOwnedSession() {
        AgentSessionMapper sessionMapper = org.mockito.Mockito.mock(AgentSessionMapper.class);
        AgentMessageMapper messageMapper = org.mockito.Mockito.mock(AgentMessageMapper.class);
        AgentRunMapper runMapper = org.mockito.Mockito.mock(AgentRunMapper.class);
        when(sessionMapper.selectOwned("AS001", 7)).thenReturn(ownedSession(7));
        when(sessionMapper.archiveOwned("AS001", 7, CLOCK.instant())).thenReturn(1);

        AgentConversationService service = new AgentConversationService(sessionMapper, messageMapper, runMapper, CLOCK);
        service.deleteSession(7, "AS001");

        verify(sessionMapper).archiveOwned("AS001", 7, CLOCK.instant());
        verifyNoInteractions(messageMapper);
        verifyNoInteractions(runMapper);
    }

    @Test
    void deleteSessionRejectsCrossUserSession() {
        AgentSessionMapper sessionMapper = org.mockito.Mockito.mock(AgentSessionMapper.class);
        AgentMessageMapper messageMapper = org.mockito.Mockito.mock(AgentMessageMapper.class);
        AgentRunMapper runMapper = org.mockito.Mockito.mock(AgentRunMapper.class);
        when(sessionMapper.selectOwned("AS001", 8)).thenReturn(null);

        AgentConversationService service = new AgentConversationService(sessionMapper, messageMapper, runMapper, CLOCK);
        assertThrows(IllegalArgumentException.class, () -> service.deleteSession(8, "AS001"));

        verify(sessionMapper, never()).archiveOwned(any(), any(Integer.class), any());
        verifyNoInteractions(messageMapper);
        verifyNoInteractions(runMapper);
    }

    private AgentSession ownedSession(int userId) {
        AgentSession session = new AgentSession();
        session.setSessionNo("AS001");
        session.setUserId(userId);
        session.setTitle("测试会话");
        session.setModel("qwen-plus");
        session.setStatus(AgentSession.STATUS_ACTIVE);
        return session;
    }
}

