package aicrediflux.token.relay.channel.mokaai;

import jakarta.servlet.http.HttpServletResponse;
import aicrediflux.token.pojo.dto.Usage;
import aicrediflux.token.relay.common.RelayInfo;

/** Moka AI 响应处理（委托 OpenAI 模式） */
public final class MokaAIRelayHandler {
    private MokaAIRelayHandler() {}
    public static Usage mokaHandler(RelayInfo info, byte[] body) throws Exception {
        HttpServletResponse r = info.getResponse();
        if (r != null && body != null) { r.getOutputStream().write(body); r.getOutputStream().flush(); }
        return new Usage();
    }
}
