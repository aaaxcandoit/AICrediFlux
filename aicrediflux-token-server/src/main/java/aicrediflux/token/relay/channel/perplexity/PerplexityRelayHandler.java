package aicrediflux.token.relay.channel.perplexity;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import aicrediflux.token.pojo.dto.Usage;
import aicrediflux.token.relay.common.RelayInfo;
import aicrediflux.token.relay.helper.RelayCommonHelper;
import aicrediflux.token.relay.helper.StreamScanner;

import java.io.InputStream;

/** Perplexity 响应处理（委托 OpenAI 模式） */
@Slf4j
public final class PerplexityRelayHandler {
    private PerplexityRelayHandler() {}
    public static Usage perplexityHandler(RelayInfo info, InputStream inputStream) throws Exception {
        HttpServletResponse r = info.getResponse();
        if (r == null || inputStream == null) return new Usage();
        StreamScanner.scan(inputStream, info, d -> { RelayCommonHelper.stringData(r, d); return true; }, r);
        RelayCommonHelper.done(r); return new Usage();
    }
}
