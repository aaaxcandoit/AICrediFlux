package aicrediflux.token.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ai.yue.library.base.convert.Convert;

import aicrediflux.token.common.ModelUtils;
import aicrediflux.token.mapper.LogMapper;
import aicrediflux.token.pojo.dto.Usage;
import aicrediflux.token.pojo.entity.Log;
import aicrediflux.token.relay.common.RelayInfo;

@ExtendWith(MockitoExtension.class)
class RelayConsumeLogServiceDispatchMetadataTest {

    @Mock
    private LogMapper logMapper;
    @Mock
    private ModelUtils modelUtils;

    @Test
    void writesDispatchMetadataIntoOtherJson() {
        RelayConsumeLogService service = new RelayConsumeLogService(logMapper, modelUtils);
        RelayInfo info = new RelayInfo();
        info.setUserId(1);
        info.setChannelId(2);
        info.setTokenId(3);
        info.setRequestId("req-1");
        info.setOriginModelName("qwen-plus");
        info.setExtraData(Map.of(
                "dispatch_source", "AGENT",
                "dispatch_session_id", "session-1",
                "dispatch_run_id", "run-1"));

        Usage usage = new Usage();
        usage.setPromptTokens(10);
        usage.setCompletionTokens(5);

        service.recordConsumeLog(info, 15, usage);

        ArgumentCaptor<Log> captor = ArgumentCaptor.forClass(Log.class);
        verify(logMapper).insert(captor.capture());
        verify(modelUtils, times(3)).addNewRecord(anyInt(), anyInt(), anyInt());

        @SuppressWarnings("unchecked")
        Map<String, Object> other = Convert.toJSONObject(captor.getValue().getOther());
        assertThat(other)
                .containsEntry("dispatch_source", "AGENT")
                .containsEntry("dispatch_session_id", "session-1")
                .containsEntry("dispatch_run_id", "run-1");
    }
}



