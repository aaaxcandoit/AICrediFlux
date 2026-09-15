package aicrediflux.token.rag.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RagChunkerTest {
    @Test
    void chunksMarkdownAndKeepsHeadingLocator() {
        RagChunker chunker = new RagChunker(40, 8);

        var chunks = chunker.chunk("DOC001", 1, "# SiliconFlow 接入\n\n配置 Base URL 和 API Key。\n\n## 计费\n\n按 AI Credit 扣费。");

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).locator()).isEqualTo("SiliconFlow 接入");
        assertThat(chunks)
                .extracting(RagChunker.Chunk::text)
                .anySatisfy(text -> assertThat(text).contains("Base URL"));
    }

    @Test
    void rejectsBlankContent() {
        RagChunker chunker = new RagChunker();

        assertThat(chunker.chunk("DOC001", 1, "   ")).isEmpty();
    }
}
