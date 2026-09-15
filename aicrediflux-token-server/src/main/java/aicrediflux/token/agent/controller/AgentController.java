package aicrediflux.token.agent.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;
import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;

import aicrediflux.token.agent.domain.AgentMessage;
import aicrediflux.token.agent.domain.AgentRun;
import aicrediflux.token.agent.domain.AgentSession;
import aicrediflux.token.agent.service.AgentConversationService;
import aicrediflux.token.agent.service.AgentRunExecutor;
import aicrediflux.token.agent.service.AgentRunSubmission;
import aicrediflux.token.agent.service.AgentSseService;
import aicrediflux.token.agent.service.AgentToolService;

@RestController
@SaCheckLogin
@RequiredArgsConstructor
@RequestMapping("/api/agent")
public class AgentController {
    private final AgentConversationService conversationService;
    private final AgentSseService sseService;
    private final AgentToolService toolService;
    private final AgentRunExecutor runExecutor;

    @GetMapping("/sessions")
    public Result<List<AgentSession>> sessions(HttpServletRequest request) {
        return R.success(conversationService.listSessions(userId(request)));
    }

    @PostMapping("/sessions")
    public Result<AgentSession> createSession(HttpServletRequest request, @RequestBody(required = false) CreateSessionRequest body) {
        CreateSessionRequest safeBody = body == null ? new CreateSessionRequest(null, null) : body;
        return R.success(conversationService.createSession(userId(request), safeBody.title(), safeBody.model()));
    }

    @PatchMapping("/sessions/{sessionNo}")
    public Result<AgentSession> renameSession(HttpServletRequest request, @PathVariable String sessionNo,
                                              @RequestBody UpdateSessionRequest body) {
        if (body == null) {
            throw new ResultException(R.errorPrompt("会话名称不能为空"));
        }
        try {
            return R.success(conversationService.renameSession(userId(request), sessionNo, body.title()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new ResultException(R.errorPrompt(e.getMessage()));
        }
    }

    @DeleteMapping("/sessions/{sessionNo}")
    public Result<?> deleteSession(HttpServletRequest request, @PathVariable String sessionNo) {
        try {
            conversationService.deleteSession(userId(request), sessionNo);
            return R.success();
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new ResultException(R.errorPrompt(e.getMessage()));
        }
    }

    @GetMapping("/sessions/{sessionNo}/messages")
    public Result<List<AgentMessage>> messages(HttpServletRequest request, @PathVariable String sessionNo) {
        return R.success(conversationService.listMessages(userId(request), sessionNo));
    }

    @PostMapping("/sessions/{sessionNo}/messages")
    public Result<AgentRunSubmission> submitMessage(HttpServletRequest request, @PathVariable String sessionNo,
                                                    @RequestBody SendMessageRequest body) {
        if (body == null) {
            throw new ResultException(R.errorPrompt("消息内容不能为空"));
        }
        try {
            AgentRunSubmission submission = conversationService.submitMessage(userId(request), sessionNo, body.model(), body.content());
            runExecutor.executeAsync(submission.runNo());
            return R.success(submission);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new ResultException(R.errorPrompt(e.getMessage()));
        }
    }

    @GetMapping("/runs/{runNo}/events")
    public SseEmitter events(HttpServletRequest request, @PathVariable String runNo) {
        return sseService.streamRun(userId(request), runNo);
    }

    @PostMapping("/runs/{runNo}/stop")
    public Result<AgentRun> stop(HttpServletRequest request, @PathVariable String runNo) {
        return R.success(conversationService.stopRun(userId(request), runNo));
    }

    @GetMapping("/tools/wallet-balance")
    public Result<AgentToolService.WalletBalanceResult> walletBalance(HttpServletRequest request) {
        return R.success(toolService.walletBalance(userId(request)));
    }

    @GetMapping("/tools/model-price")
    public Result<AgentToolService.ModelPriceResult> modelPrice(
            @RequestParam String model,
            @RequestParam(required = false) String group,
            @RequestParam(required = false, defaultValue = "1000") int promptTokens,
            @RequestParam(required = false, defaultValue = "1000") int completionTokens) {
        return R.success(toolService.modelPrice(model, group, promptTokens, completionTokens));
    }

    @GetMapping("/tools/usage")
    public Result<AgentToolService.UsageSummaryResult> usage(HttpServletRequest request,
                                                             @RequestParam(required = false) Integer hours,
                                                             @RequestParam(required = false) String model,
                                                             @RequestParam(required = false) Integer tokenId) {
        return R.success(toolService.usageSummary(userId(request), hours, model, tokenId));
    }

    @GetMapping("/tools/channel-status")
    public Result<AgentToolService.ChannelStatusResult> channelStatus(HttpServletRequest request,
                                                                      @RequestParam(required = false) String model,
                                                                      @RequestParam(required = false) Integer hours) {
        try {
            return R.success(toolService.channelStatus(role(request), model, hours));
        } catch (IllegalStateException e) {
            throw new ResultException(R.errorPrompt(e.getMessage()));
        }
    }

    private int userId(HttpServletRequest request) {
        Object id = request.getAttribute("id");
        if (!(id instanceof Integer userId) || userId <= 0) {
            throw new ResultException(R.errorPrompt("用户未登录"));
        }
        return userId;
    }

    private int role(HttpServletRequest request) {
        Object role = request.getAttribute("role");
        if (!(role instanceof Integer value)) {
            return 1;
        }
        return value;
    }

    public record CreateSessionRequest(String title, String model) {}
    public record UpdateSessionRequest(String title) {}
    public record SendMessageRequest(String content, String model) {}
}
