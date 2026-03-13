package cn.sdh.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 问答请求
 */
@Data
public class AskRequest {

    @NotBlank(message = "问题不能为空")
    private String question;

    private String sessionId;
}
