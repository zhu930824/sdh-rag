package cn.sdh.backend.agent;

import cn.sdh.backend.entity.AgentRun;
import cn.sdh.backend.entity.AgentTool;
import com.alibaba.fastjson2.JSON;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Fingerprints execution inputs without persisting credentials, endpoint URLs or prompt bodies. */
final class AgentExecutionConfiguration {
    private AgentExecutionConfiguration() {}
    static Map<String, Object> capture(AgentAskService.Prepared p, AgentRun run) {
        var config = p.modelBinding().config();
        String engine = "agentscope".equals(run.getEngine()) ? "agentscope" : "legacy";
        return map("engine", map("name", engine, "version", AgentRuntimeVersion.current(engine), "originalRunVersion", run.getEngineVersion()),
                "model", map("configurationId", config.getId(), "provider", p.provider(), "modelId", config.getModelId(),
                        "temperatureConfigured", config.getTemperature(), "maxTokensConfigured", config.getMaxTokens(),
                        "endpointSha256", hash(config.getBaseUrl()), "configurationUpdatedAt", config.getUpdateTime()),
                "prompt", map("systemSha256", hash(p.system()), "resumeNoteSha256", hash(p.resumeNote())),
                "tools", p.tools().stream().sorted(Comparator.comparing(t -> String.valueOf(t.getCode())))
                        .map(AgentExecutionConfiguration::tool).toList(),
                "evidence", map("knowledgeId", p.kbId(), "evidenceSha256", hash(p.evidenceText()),
                        "publicationVersion", p.kbId() == null ? "NOT_APPLICABLE" : "UNPINNED"),
                "execution", map("memoryEnabled", p.memoryEnabled(), "maxSteps", p.maxSteps()));
    }
    private static Map<String, Object> tool(AgentTool tool) {
        return map("id", tool.getId(), "code", tool.getCode(), "definitionSha256", ToolDefinitionFingerprint.of(tool),
                "nameSha256", hash(tool.getName()), "descriptionSha256", hash(tool.getDescription()),
                "timeoutSeconds", tool.getTimeoutSeconds(), "status", tool.getStatus());
    }
    static TreeMap<String, Object> map(Object... fields) {
        var map = new TreeMap<String, Object>();
        for (int i = 0; i < fields.length; i += 2) map.put((String) fields[i], fields[i + 1]);
        return map;
    }
    static String version(Object value) { return hash(JSON.toJSONString(value)); }
    static String hash(String value) {
        if (value == null) return null;
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
