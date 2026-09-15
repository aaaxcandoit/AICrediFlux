package aicrediflux.token.agent.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import aicrediflux.token.agent.domain.AgentMessage;
import aicrediflux.token.agent.domain.AgentRun;
import aicrediflux.token.agent.domain.AgentSession;
import aicrediflux.token.agent.mapper.AgentMessageMapper;
import aicrediflux.token.agent.mapper.AgentRunMapper;
import aicrediflux.token.agent.mapper.AgentSessionMapper;

@Service
public class AgentConversationService {
    private static final int DEFAULT_SESSION_LIMIT = 50;
    private static final String DEFAULT_TITLE = "AICrediFlux Copilot";
    private static final String DEFAULT_MODEL = "gpt-4o-mini";

    private final AgentSessionMapper sessionMapper;
    private final AgentMessageMapper messageMapper;
    private final AgentRunMapper runMapper;
    private final Clock clock;

    @Autowired
    public AgentConversationService(AgentSessionMapper sessionMapper, AgentMessageMapper messageMapper,
                                    AgentRunMapper runMapper) {
        this(sessionMapper, messageMapper, runMapper, Clock.systemUTC());
    }

    AgentConversationService(AgentSessionMapper sessionMapper, AgentMessageMapper messageMapper,
                             AgentRunMapper runMapper, Clock clock) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.runMapper = runMapper;
        this.clock = clock;
    }

    @Transactional
    public AgentSession createSession(int userId, String title, String model) {
        AgentSession session = new AgentSession();
        session.setSessionNo(nextNo("AS"));
        session.setUserId(userId);
        session.setTitle(normalizeTitle(title));
        session.setModel(normalizeModel(model));
        session.setStatus(AgentSession.STATUS_ACTIVE);
        session.setCreateTime(clock.instant());
        session.setUpdateTime(clock.instant());
        if (sessionMapper.insert(session) != 1) {
            throw new IllegalStateException("创建 Copilot 会话失败");
        }
        return session;
    }

    public List<AgentSession> listSessions(int userId) {
        return sessionMapper.selectByUserId(userId, DEFAULT_SESSION_LIMIT);
    }

    public List<AgentMessage> listMessages(int userId, String sessionNo) {
        assertOwnedSession(userId, sessionNo);
        return messageMapper.selectBySessionNo(sessionNo);
    }

    @Transactional
    public AgentRunSubmission submitMessage(int userId, String sessionNo, String model, String content) {
        AgentSession session = assertOwnedSession(userId, sessionNo);
        if (runMapper.selectRunningBySessionNo(sessionNo) != null) {
            throw new IllegalStateException("当前会话已有运行中的 Copilot 任务");
        }
        String normalizedContent = normalizeContent(content);
        Instant now = clock.instant();

        AgentMessage message = new AgentMessage();
        message.setMessageNo(nextNo("AM"));
        message.setSessionNo(sessionNo);
        message.setUserId(userId);
        message.setRole(AgentMessage.ROLE_USER);
        message.setContent(normalizedContent);
        message.setCreateTime(now);
        if (messageMapper.insert(message) != 1) {
            throw new IllegalStateException("保存 Copilot 消息失败");
        }

        AgentRun run = new AgentRun();
        run.setRunNo(nextNo("AR"));
        run.setSessionNo(sessionNo);
        run.setUserId(userId);
        run.setModel(normalizeModel(model == null || model.isBlank() ? session.getModel() : model));
        run.setStatus(AgentRun.STATUS_RUNNING);
        run.setRequestId(nextNo("req-agent"));
        run.setSource("AGENT");
        run.setStartedAt(now);
        run.setTotalPromptTokens(0);
        run.setTotalCompletionTokens(0);
        run.setTotalQuota(0L);
        run.setStopRequested(0);
        run.setCreateTime(now);
        run.setUpdateTime(now);
        if (runMapper.insert(run) != 1) {
            throw new IllegalStateException("创建 Copilot 运行记录失败");
        }
        return new AgentRunSubmission(sessionNo, run.getRunNo(), run.getStatus());
    }

    @Transactional
    public AgentRun stopRun(int userId, String runNo) {
        AgentRun run = runMapper.selectOwned(runNo, userId);
        if (run == null) {
            throw new IllegalArgumentException("Copilot 运行记录不存在");
        }
        if (AgentRun.ACTIVE_STATUSES.contains(run.getStatus())) {
            runMapper.requestStop(runNo, userId, clock.instant());
            run.setStopRequested(1);
        }
        return run;
    }

    @Transactional
    public AgentSession renameSession(int userId, String sessionNo, String title) {
        AgentSession session = assertOwnedSession(userId, sessionNo);
        String normalizedTitle = normalizeRequiredTitle(title);
        Instant now = clock.instant();
        if (sessionMapper.updateTitle(sessionNo, userId, normalizedTitle, now) != 1) {
            throw new IllegalStateException("更新 Copilot 会话名称失败");
        }
        session.setTitle(normalizedTitle);
        session.setUpdateTime(now);
        return session;
    }

    @Transactional
    public void deleteSession(int userId, String sessionNo) {
        assertOwnedSession(userId, sessionNo);
        if (sessionMapper.archiveOwned(sessionNo, userId, clock.instant()) != 1) {
            throw new IllegalStateException("删除 Copilot 会话失败");
        }
    }

    public AgentRun getOwnedRun(int userId, String runNo) {
        AgentRun run = runMapper.selectOwned(runNo, userId);
        if (run == null) {
            throw new IllegalArgumentException("Copilot 运行记录不存在");
        }
        return run;
    }

    private AgentSession assertOwnedSession(int userId, String sessionNo) {
        AgentSession session = sessionMapper.selectOwned(sessionNo, userId);
        if (session == null) {
            throw new IllegalArgumentException("Copilot 会话不存在");
        }
        return session;
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return DEFAULT_TITLE;
        }
        String trimmed = title.trim();
        return trimmed.length() > 120 ? trimmed.substring(0, 120) : trimmed;
    }

    private String normalizeRequiredTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("会话名称不能为空");
        }
        return normalizeTitle(title);
    }

    private String normalizeModel(String model) {
        if (model == null || model.isBlank()) {
            return DEFAULT_MODEL;
        }
        String trimmed = model.trim();
        return trimmed.length() > 128 ? trimmed.substring(0, 128) : trimmed;
    }

    private String normalizeContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
        return content.trim();
    }

    private String nextNo(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}


