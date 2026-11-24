package com.dsh.agent;

import com.dsh.models.Json;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * append-only 文件事件日志（每会话一个 JSONL）。
 * 用于跨进程续跑：进程被杀后，下一次从文件恢复 llm_view。
 */
public final class FileAgentStore implements AgentStore {

    private final File dir;

    public FileAgentStore(File dir) {
        this.dir = dir;
        if (!dir.exists()) dir.mkdirs();
    }

    private File file(String sessionId) {
        return new File(dir, sessionId.replaceAll("[^a-zA-Z0-9_.-]", "_") + ".jsonl");
    }

    @Override public synchronized long append(String sessionId, String type, String payload) {
        List<AgentEvent> existing = events(sessionId);
        long seq = existing.size() + 1;
        long at = System.currentTimeMillis();
        String line = "{\"seq\":" + seq
                + ",\"type\":" + Json.quote(type)
                + ",\"payload\":" + Json.quote(payload)
                + ",\"at\":" + at + "}";
        try (OutputStreamWriter w = new OutputStreamWriter(
                new FileOutputStream(file(sessionId), true), StandardCharsets.UTF_8)) {
            w.write(line);
            w.write('\n');
        } catch (Throwable ignored) {
        }
        return seq;
    }

    @Override public synchronized List<AgentEvent> events(String sessionId) {
        List<AgentEvent> out = new ArrayList<>();
        File f = file(sessionId);
        if (!f.exists()) return out;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                Map<String, Object> m = Json.asObject(Json.parse(line));
                if (m == null) continue;
                Object seq = m.get("seq");
                Object at = m.get("at");
                out.add(new AgentEvent(seq instanceof Number ? ((Number) seq).longValue() : out.size() + 1,
                        Json.string(m.get("type")), Json.string(m.get("payload")),
                        at instanceof Number ? ((Number) at).longValue() : 0));
            }
        } catch (Throwable ignored) {
        }
        return out;
    }
}
