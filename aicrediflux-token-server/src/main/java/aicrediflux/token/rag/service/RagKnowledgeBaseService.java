package aicrediflux.token.rag.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ai.yue.library.base.convert.Convert;

import aicrediflux.token.rag.config.RagKnowledgeProperties;
import aicrediflux.token.rag.domain.RagChunk;
import aicrediflux.token.rag.domain.RagDocument;
import aicrediflux.token.rag.domain.RagIndexJob;
import aicrediflux.token.rag.mapper.RagChunkMapper;
import aicrediflux.token.rag.mapper.RagDocumentMapper;
import aicrediflux.token.rag.mapper.RagIndexJobMapper;

@Service
public class RagKnowledgeBaseService {
    private final RagDocumentMapper documentMapper;
    private final RagChunkMapper chunkMapper;
    private final RagIndexJobMapper jobMapper;
    private final RagVectorGateway vectorGateway;
    private final RagKnowledgeProperties properties;
    private final Clock clock;

    @Autowired
    public RagKnowledgeBaseService(RagDocumentMapper documentMapper, RagChunkMapper chunkMapper,
                                   RagIndexJobMapper jobMapper, RagVectorGateway vectorGateway,
                                   RagKnowledgeProperties properties) {
        this(documentMapper, chunkMapper, jobMapper, vectorGateway, properties, Clock.systemUTC());
    }

    RagKnowledgeBaseService(RagDocumentMapper documentMapper, RagChunkMapper chunkMapper,
                            RagIndexJobMapper jobMapper, RagVectorGateway vectorGateway,
                            RagKnowledgeProperties properties, Clock clock) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.jobMapper = jobMapper;
        this.vectorGateway = vectorGateway;
        this.properties = properties;
        this.clock = clock;
    }

    public List<RagDocument> listDocuments(int limit) {
        return documentMapper.selectRecent(Math.min(Math.max(limit, 1), 500));
    }

    @Transactional
    public RagDocument saveTextDocument(int userId, SaveTextDocumentCommand command) {
        validate(command);
        String contentHash = sha256(command.content());
        RagDocument same = documentMapper.selectSameContent(command.space(), command.title().trim(), contentHash);
        if (same != null) {
            return same;
        }
        Instant now = Instant.now(clock);
        RagDocument document = new RagDocument();
        document.setDocNo("RD" + UUID.randomUUID().toString().replace("-", ""));
        document.setSpace(command.space());
        document.setTitle(command.title().trim());
        document.setSourceType(command.sourceType() == null || command.sourceType().isBlank() ? "UPLOAD" : command.sourceType());
        document.setSourceName(command.sourceName());
        document.setContentHash(contentHash);
        document.setVersion(documentMapper.selectMaxVersion(command.space(), command.title().trim()) + 1);
        document.setStatus(RagDocument.STATUS_PENDING);
        document.setChunkCount(0);
        document.setCreatedBy(userId);
        document.setCreateTime(now);
        document.setUpdateTime(now);
        if (documentMapper.insert(document) != 1) {
            throw new IllegalStateException("保存 RAG 文档失败");
        }
        indexContent(document, command.content(), userId);
        return documentMapper.selectByDocNo(document.getDocNo());
    }

    @Transactional
    public RagDocument indexDocument(String docNo, String content, int userId) {
        RagDocument document = documentMapper.selectByDocNo(docNo);
        if (document == null) {
            throw new IllegalArgumentException("文档不存在");
        }
        indexContent(document, resolveContent(document, content), userId);
        return documentMapper.selectByDocNo(docNo);
    }

    @Transactional
    public List<RagDocument> indexProjectDocs(int userId) {
        Path docsDir = projectDocsDir();
        if (!Files.isDirectory(docsDir)) {
            throw new IllegalStateException("未找到 docs 目录: " + docsDir);
        }
        List<RagDocument> result = new ArrayList<>();
        try (var stream = Files.list(docsDir)) {
            for (Path path : stream.filter(p -> p.getFileName().toString().endsWith(".md")).sorted().toList()) {
                String fileName = path.getFileName().toString();
                String title = fileName.substring(0, fileName.length() - 3);
                String content = Files.readString(path, StandardCharsets.UTF_8);
                result.add(saveTextDocument(userId, new SaveTextDocumentCommand("platform_docs", title, fileName, content, "PROJECT_DOCS")));
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取项目 docs 失败: " + e.getMessage(), e);
        }
        return result;
    }

    @Transactional
    public void deleteDocument(String docNo) {
        RagDocument document = documentMapper.selectByDocNo(docNo);
        if (document == null) {
            return;
        }
        vectorGateway.deleteDocument(docNo);
        chunkMapper.deleteByDocNo(docNo);
        documentMapper.markDeleted(docNo, Instant.now(clock));
    }

    private void indexContent(RagDocument document, String content, int userId) {
        Instant now = Instant.now(clock);
        RagIndexJob job = new RagIndexJob();
        job.setJobNo("RJ" + UUID.randomUUID().toString().replace("-", ""));
        job.setDocNo(document.getDocNo());
        job.setStatus(RagIndexJob.STATUS_RUNNING);
        job.setRequestedBy(userId);
        job.setCreateTime(now);
        job.setUpdateTime(now);
        if (jobMapper.insert(job) != 1) {
            throw new IllegalStateException("创建 RAG 索引任务失败");
        }
        documentMapper.markStatus(document.getDocNo(), RagDocument.STATUS_INDEXING, 0, null, null, now);
        List<RagChunker.Chunk> chunks = new RagChunker(properties.getChunkSize(), properties.getChunkOverlap())
                .chunk(document.getDocNo(), document.getVersion(), content);
        List<RagChunk> entities = chunks.stream().map(chunk -> toEntity(document, chunk)).toList();
        chunkMapper.deleteByDocNo(document.getDocNo());
        for (RagChunk chunk : entities) {
            if (chunkMapper.insert(chunk) != 1) {
                throw new IllegalStateException("保存 RAG 分块失败");
            }
        }
        try {
            vectorGateway.indexDocument(document, entities);
            Instant indexedAt = Instant.now(clock);
            documentMapper.markStatus(document.getDocNo(), RagDocument.STATUS_READY, entities.size(), null, indexedAt, indexedAt);
            jobMapper.finish(job.getJobNo(), RagIndexJob.STATUS_SUCCEEDED, entities.size(), entities.size(), null, indexedAt);
        } catch (RagUnavailableException e) {
            markIndexFailed(document, job, entities.size(), e.getMessage());
        } catch (RuntimeException e) {
            markIndexFailed(document, job, entities.size(), "知识库索引失败：" + rootMessage(e));
        }
    }

    private void markIndexFailed(RagDocument document, RagIndexJob job, int chunkCount, String message) {
        String trimmed = trim(message);
        Instant failedAt = Instant.now(clock);
        documentMapper.markStatus(document.getDocNo(), RagDocument.STATUS_FAILED, chunkCount, trimmed, null, failedAt);
        jobMapper.finish(job.getJobNo(), RagIndexJob.STATUS_FAILED, chunkCount, 0, trimmed, failedAt);
    }

    private String resolveContent(RagDocument document, String content) {
        if (content != null && !content.isBlank()) {
            return content;
        }
        List<RagChunk> chunks = chunkMapper.selectByDocumentVersion(document.getDocNo(), document.getVersion());
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("重新索引需要文档内容");
        }
        return chunks.stream().map(RagChunk::getText).reduce((left, right) -> left + "\n\n" + right).orElse("");
    }

    private RagChunk toEntity(RagDocument document, RagChunker.Chunk chunk) {
        RagChunk entity = new RagChunk();
        entity.setDocNo(document.getDocNo());
        entity.setVersion(document.getVersion());
        entity.setChunkNo(chunk.chunkNo());
        entity.setLocator(chunk.locator());
        entity.setText(chunk.text());
        entity.setContentHash(sha256(chunk.text()));
        entity.setMetadataJson(Convert.toJSONString(new ChunkMetadata(document.getSpace(), document.getDocNo(), document.getTitle(), document.getContentHash(), chunk.locator())));
        entity.setVectorId(vectorId(document, chunk));
        entity.setCreateTime(Instant.now(clock));
        return entity;
    }

    private String vectorId(RagDocument document, RagChunker.Chunk chunk) {
        return "RV" + sha256(document.getDocNo() + ":" + document.getVersion() + ":" + chunk.chunkNo()).substring(0, 30);
    }

    private void validate(SaveTextDocumentCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("文档不能为空");
        }
        if (!properties.getSpaces().contains(command.space())) {
            throw new IllegalArgumentException("知识空间不支持: " + command.space());
        }
        if (command.title() == null || command.title().isBlank()) {
            throw new IllegalArgumentException("文档标题不能为空");
        }
        if (command.content() == null || command.content().isBlank()) {
            throw new IllegalArgumentException("文档内容不能为空");
        }
        if (command.content().getBytes(StandardCharsets.UTF_8).length > properties.getMaxFileBytes()) {
            throw new IllegalArgumentException("文档超过大小限制");
        }
        String sourceName = command.sourceName() == null ? "" : command.sourceName().toLowerCase(Locale.ROOT);
        if (!sourceName.isBlank() && !(sourceName.endsWith(".md") || sourceName.endsWith(".txt"))) {
            throw new IllegalArgumentException("阶段 4 第一版仅支持 .md / .txt");
        }
    }

    private Path projectDocsDir() {
        Path userDir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path direct = userDir.resolve("docs");
        if (Files.isDirectory(direct)) {
            return direct;
        }
        Path parent = userDir.getParent();
        return parent == null ? direct : parent.resolve("docs");
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("计算文档哈希失败", e);
        }
    }

    private String trim(String message) {
        if (message == null || message.isBlank()) {
            return "知识库暂不可用";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        String message = null;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                message = current.getMessage();
            }
            current = current.getCause();
        }
        return message == null || message.isBlank() ? "未知错误" : message;
    }

    public record SaveTextDocumentCommand(String space, String title, String sourceName, String content, String sourceType) {}
    private record ChunkMetadata(String space, String docId, String title, String contentHash, String locator) {}
}
