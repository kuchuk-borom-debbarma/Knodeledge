package dev.kuku.knodeledge.services.rag;

import dev.kuku.knodeledge.services.rag.model.ChunkText;
import dev.kuku.knodeledge.services.rag.model.Note;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class NoteChunker {
    static final int TARGET_TOKENS = 650;
    static final int MAX_TOKENS = 800;
    static final int OVERLAP_TOKENS = 100;

    public List<ChunkText> chunk(Note note) {
        List<String> bodyChunks = chunkBody(note.content());
        List<ChunkText> result = new ArrayList<>();
        String created = DateTimeFormatter.ISO_LOCAL_DATE.format(
            note.createdAt().atOffset(ZoneOffset.UTC)
        );
        for (int index = 0; index < bodyChunks.size(); index++) {
            String content = """
                Note title: %s
                Created date: %s

                %s
                """.formatted(note.title(), created, bodyChunks.get(index).trim()).trim();
            result.add(new ChunkText(index, content));
        }
        return result;
    }

    private List<String> chunkBody(String content) {
        List<String> blocks = splitIntoSemanticBlocks(content);
        List<String> chunks = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int currentTokens = 0;

        for (String block : blocks) {
            int blockTokens = estimateTokens(block);
            if (blockTokens > MAX_TOKENS) {
                flushCurrent(chunks, current);
                currentTokens = 0;
                chunks.addAll(splitLargeBlock(block));
                continue;
            }

            if (!current.isEmpty() && currentTokens + blockTokens > MAX_TOKENS) {
                flushCurrent(chunks, current);
                current = overlapFrom(chunks.get(chunks.size() - 1));
                currentTokens = current.stream().mapToInt(this::estimateTokens).sum();
            }

            current.add(block);
            currentTokens += blockTokens;

            if (currentTokens >= TARGET_TOKENS) {
                flushCurrent(chunks, current);
                current = overlapFrom(chunks.get(chunks.size() - 1));
                currentTokens = current.stream().mapToInt(this::estimateTokens).sum();
            }
        }

        flushCurrent(chunks, current);
        return chunks.stream()
            .map(String::trim)
            .filter(text -> !text.isBlank())
            .toList();
    }

    private List<String> splitIntoSemanticBlocks(String content) {
        List<String> blocks = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) {
                flushParagraph(blocks, paragraph);
                continue;
            }
            if (isHeading(trimmed) || isBullet(trimmed)) {
                flushParagraph(blocks, paragraph);
                blocks.add(trimmed);
                continue;
            }
            if (!paragraph.isEmpty()) {
                paragraph.append('\n');
            }
            paragraph.append(trimmed);
        }
        flushParagraph(blocks, paragraph);
        return blocks.isEmpty() ? List.of(content.trim()) : blocks;
    }

    private boolean isHeading(String line) {
        return line.matches("#{1,6}\\s+.+") || line.matches("[A-Z][A-Za-z0-9 ,:'\\-/]{1,80}:");
    }

    private boolean isBullet(String line) {
        return line.matches("([-*+]\\s+|\\d+[.)]\\s+).+");
    }

    private void flushParagraph(List<String> blocks, StringBuilder paragraph) {
        if (!paragraph.isEmpty()) {
            blocks.add(paragraph.toString());
            paragraph.setLength(0);
        }
    }

    private void flushCurrent(List<String> chunks, List<String> current) {
        if (!current.isEmpty()) {
            chunks.add(String.join("\n\n", current));
            current.clear();
        }
    }

    private List<String> splitLargeBlock(String block) {
        List<String> words = words(block);
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < words.size()) {
            int end = Math.min(start + TARGET_TOKENS, words.size());
            chunks.add(String.join(" ", words.subList(start, end)));
            if (end == words.size()) {
                break;
            }
            start = Math.max(0, end - OVERLAP_TOKENS);
        }
        return chunks;
    }

    private List<String> overlapFrom(String previousChunk) {
        List<String> words = words(previousChunk);
        if (words.size() <= OVERLAP_TOKENS) {
            return new ArrayList<>(List.of(previousChunk));
        }
        return new ArrayList<>(List.of(String.join(
            " ",
            words.subList(words.size() - OVERLAP_TOKENS, words.size())
        )));
    }

    private int estimateTokens(String text) {
        return words(text).size();
    }

    private List<String> words(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.trim().split("\\s+"))
            .filter(word -> !word.isBlank())
            .toList();
    }
}
