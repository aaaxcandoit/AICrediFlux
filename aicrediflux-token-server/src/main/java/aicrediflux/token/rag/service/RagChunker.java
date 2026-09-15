package aicrediflux.token.rag.service;

import java.util.ArrayList;
import java.util.List;

public class RagChunker {
    private static final int DEFAULT_CHUNK_SIZE = 600;
    private static final int DEFAULT_OVERLAP = 100;

    private final int chunkSize;
    private final int overlap;

    public RagChunker() {
        this(DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    public RagChunker(int chunkSize, int overlap) {
        this.chunkSize = Math.max(40, chunkSize);
        this.overlap = Math.max(0, Math.min(overlap, this.chunkSize / 2));
    }

    public List<Chunk> chunk(String docNo, int version, String content) {
        String normalized = normalize(content);
        if (normalized.isBlank()) {
            return List.of();
        }
        List<Chunk> chunks = new ArrayList<>();
        String currentHeading = "文档开头";
        StringBuilder buffer = new StringBuilder();
        for (String line : normalized.split("\\R")) {
            String heading = headingText(line);
            if (heading != null) {
                flush(docNo, version, chunks, currentHeading, buffer);
                currentHeading = heading;
            }
            if (!line.isBlank()) {
                buffer.append(line).append('\n');
            }
            if (buffer.length() >= chunkSize) {
                flush(docNo, version, chunks, currentHeading, buffer);
            }
        }
        flush(docNo, version, chunks, currentHeading, buffer);
        return chunks;
    }

    private void flush(String docNo, int version, List<Chunk> chunks, String locator, StringBuilder buffer) {
        String text = buffer.toString().trim();
        if (text.isBlank()) {
            buffer.setLength(0);
            return;
        }
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + chunkSize);
            String slice = text.substring(start, end).trim();
            if (!slice.isBlank()) {
                chunks.add(new Chunk(docNo, version, chunks.size(), locator, slice));
            }
            if (end >= text.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        buffer.setLength(0);
    }

    private String headingText(String line) {
        String trimmed = line == null ? "" : line.trim();
        if (!trimmed.startsWith("#")) {
            return null;
        }
        int index = 0;
        while (index < trimmed.length() && trimmed.charAt(index) == '#') {
            index++;
        }
        if (index == 0 || index >= trimmed.length() || !Character.isWhitespace(trimmed.charAt(index))) {
            return null;
        }
        String heading = trimmed.substring(index).trim();
        return heading.isBlank() ? null : heading;
    }

    private String normalize(String content) {
        if (content == null) {
            return "";
        }
        return content.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    public record Chunk(String docNo, int version, int chunkNo, String locator, String text) {
    }
}
