package aicrediflux.token.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import aicrediflux.token.rag.config.RagKnowledgeProperties;
import aicrediflux.token.rag.domain.RagChunk;
import aicrediflux.token.rag.domain.RagDocument;
import aicrediflux.token.rag.domain.RagIndexJob;
import aicrediflux.token.rag.mapper.RagChunkMapper;
import aicrediflux.token.rag.mapper.RagDocumentMapper;
import aicrediflux.token.rag.mapper.RagIndexJobMapper;

class RagKnowledgeBaseServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-14T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void saveDocumentRecordsFailedStatusWhenVectorIndexThrowsRuntimeException() {
        RagDocumentMapper documentMapper = mock(RagDocumentMapper.class);
        RagChunkMapper chunkMapper = mock(RagChunkMapper.class);
        RagIndexJobMapper jobMapper = mock(RagIndexJobMapper.class);
        RagVectorGateway vectorGateway = mock(RagVectorGateway.class);
        RagKnowledgeProperties properties = new RagKnowledgeProperties();
        properties.setChunkSize(80);
        properties.setChunkOverlap(10);
        RagDocument stored = new RagDocument();
        stored.setDocNo("RD-stored");
        stored.setStatus(RagDocument.STATUS_FAILED);

        when(documentMapper.selectSameContent(any(), any(), any())).thenReturn(null);
        when(documentMapper.selectMaxVersion(any(), any())).thenReturn(0);
        when(documentMapper.insert(any(RagDocument.class))).thenReturn(1);
        when(jobMapper.insert(any(RagIndexJob.class))).thenReturn(1);
        when(chunkMapper.insert(any(RagChunk.class))).thenReturn(1);
        when(documentMapper.selectByDocNo(any())).thenReturn(stored);
        org.mockito.Mockito.doThrow(new IllegalStateException("SiliconFlow upstream rejected embedding request"))
                .when(vectorGateway).indexDocument(any(RagDocument.class), any(List.class));

        RagKnowledgeBaseService service = new RagKnowledgeBaseService(
                documentMapper, chunkMapper, jobMapper, vectorGateway, properties, CLOCK);

        assertThatCode(() -> service.saveTextDocument(7, new RagKnowledgeBaseService.SaveTextDocumentCommand(
                "platform_docs",
                "SiliconFlow Embedding 接入指南",
                "siliconflow.md",
                "# SiliconFlow Embedding 接入指南\n\n配置 Qwen/Qwen3-Embedding-0.6B 用于 RAG 索引。",
                "UPLOAD")))
                .doesNotThrowAnyException();

        verify(documentMapper).markStatus(any(), eq(RagDocument.STATUS_FAILED), anyInt(),
                contains("SiliconFlow upstream rejected embedding request"), isNull(), any(Instant.class));
        ArgumentCaptor<List> chunksCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorGateway).indexDocument(any(RagDocument.class), chunksCaptor.capture());
        RagChunk indexedChunk = (RagChunk) chunksCaptor.getValue().get(0);
        assertThat(indexedChunk.getVectorId()).startsWith("RV").hasSizeLessThanOrEqualTo(36);

        verify(jobMapper).finish(any(), eq(RagIndexJob.STATUS_FAILED), anyInt(), eq(0),
                contains("SiliconFlow upstream rejected embedding request"), any(Instant.class));
    }
}
