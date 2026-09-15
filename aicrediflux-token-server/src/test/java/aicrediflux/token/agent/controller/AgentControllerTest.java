package aicrediflux.token.agent.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import aicrediflux.token.agent.service.AgentConversationService;
import aicrediflux.token.agent.service.AgentRunExecutor;
import aicrediflux.token.agent.service.AgentRunSubmission;
import aicrediflux.token.agent.service.AgentSseService;
import aicrediflux.token.agent.service.AgentToolService;

class AgentControllerTest {
    @Test
    void submitMessageStartsRunExecution() {
        AgentConversationService conversationService = org.mockito.Mockito.mock(AgentConversationService.class);
        AgentSseService sseService = org.mockito.Mockito.mock(AgentSseService.class);
        AgentToolService toolService = org.mockito.Mockito.mock(AgentToolService.class);
        AgentRunExecutor runExecutor = org.mockito.Mockito.mock(AgentRunExecutor.class);
        HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
        when(request.getAttribute("id")).thenReturn(7);
        when(conversationService.submitMessage(7, "AS001", "qwen-plus", "你好"))
                .thenReturn(new AgentRunSubmission("AS001", "AR001", "RUNNING"));

        AgentController controller = new AgentController(conversationService, sseService, toolService, runExecutor);
        var result = controller.submitMessage(request, "AS001", new AgentController.SendMessageRequest("你好", "qwen-plus"));

        assertThat(result.getData().runNo()).isEqualTo("AR001");
        verify(runExecutor).executeAsync("AR001");
    }
    @Test
    void renameSessionDelegatesToConversationService() {
        AgentConversationService conversationService = org.mockito.Mockito.mock(AgentConversationService.class);
        AgentSseService sseService = org.mockito.Mockito.mock(AgentSseService.class);
        AgentToolService toolService = org.mockito.Mockito.mock(AgentToolService.class);
        AgentRunExecutor runExecutor = org.mockito.Mockito.mock(AgentRunExecutor.class);
        HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
        aicrediflux.token.agent.domain.AgentSession session = new aicrediflux.token.agent.domain.AgentSession();
        session.setSessionNo("AS001");
        session.setTitle("成本复盘");
        when(request.getAttribute("id")).thenReturn(7);
        when(conversationService.renameSession(7, "AS001", "成本复盘")).thenReturn(session);

        AgentController controller = new AgentController(conversationService, sseService, toolService, runExecutor);
        var result = controller.renameSession(request, "AS001", new AgentController.UpdateSessionRequest("成本复盘"));

        assertThat(result.getData().getTitle()).isEqualTo("成本复盘");
        verify(conversationService).renameSession(7, "AS001", "成本复盘");
    }
    @Test
    void deleteSessionDelegatesToConversationService() {
        AgentConversationService conversationService = org.mockito.Mockito.mock(AgentConversationService.class);
        AgentSseService sseService = org.mockito.Mockito.mock(AgentSseService.class);
        AgentToolService toolService = org.mockito.Mockito.mock(AgentToolService.class);
        AgentRunExecutor runExecutor = org.mockito.Mockito.mock(AgentRunExecutor.class);
        HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
        when(request.getAttribute("id")).thenReturn(7);

        AgentController controller = new AgentController(conversationService, sseService, toolService, runExecutor);
        var result = controller.deleteSession(request, "AS001");

        assertThat(result).isNotNull();
        verify(conversationService).deleteSession(7, "AS001");
    }
}
