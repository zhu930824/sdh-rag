package cn.sdh.backend.agent;

import io.agentscope.core.ReActAgent;
import java.util.Properties;

/** Read the executing artifact version; an original Run's version can differ after an upgrade. */
public final class AgentRuntimeVersion {
    private AgentRuntimeVersion() {}
    private static final String SDK = sdkVersion();
    public static String current(String engine) { return "agentscope".equals(engine) ? SDK : "1"; }
    private static String sdkVersion() {
        try (var input = ReActAgent.class.getResourceAsStream("/META-INF/maven/io.agentscope/agentscope-core/pom.properties")) {
            if (input != null) {
                var properties = new Properties(); properties.load(input);
                String version = properties.getProperty("version");
                if (version != null && !version.isBlank()) return version;
            }
        } catch (java.io.IOException ignored) { /* Missing build metadata must remain explicit. */ }
        return "UNKNOWN";
    }
}
