package cn.sdh.backend.agent;

import cn.sdh.backend.entity.AgentTool;
import com.alibaba.fastjson2.JSON;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/** Bind approval to the destination, authentication reference, schema and business policy. */
public final class ToolDefinitionFingerprint {
    private ToolDefinitionFingerprint() {}
    public static String of(AgentTool tool) {
        String definition = JSON.toJSONString(Arrays.asList(tool.getCode(), tool.getEndpoint(),
                tool.getHttpMethod(), tool.getAuthType(), tool.getAuthHeader(), tool.getCredentialRef(),
                tool.getInputSchema(), tool.getRiskLevel(), tool.getApprovalMode(), tool.getApproverId(),
                tool.getAllowPrivateNetwork(), tool.getAllowInsecureHttp()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(definition.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
