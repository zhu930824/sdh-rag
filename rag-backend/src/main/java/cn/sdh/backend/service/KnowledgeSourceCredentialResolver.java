package cn.sdh.backend.service;

import cn.sdh.backend.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KnowledgeSourceCredentialResolver {
    private final Environment environment;

    public String resolve(String credentialRef) {
        if (credentialRef == null || credentialRef.isBlank()) return null;
        if (!credentialRef.startsWith("env:")) {
            throw new BusinessException(400, "当前仅支持 env:VARIABLE_NAME 格式的凭据引用");
        }
        String name = credentialRef.substring(4);
        if (!name.matches("[A-Za-z_][A-Za-z0-9_]{0,127}")) {
            throw new BusinessException(400, "环境变量凭据引用格式不正确");
        }
        String value = environment.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new BusinessException(500, "未找到数据源凭据环境变量: " + name);
        }
        return value;
    }
}
