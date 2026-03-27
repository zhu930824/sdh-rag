package cn.sdh.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SensitiveWordRequest {

    private Long id;

    @NotBlank(message = "敏感词不能为空")
    private String word;

    @NotBlank(message = "分类不能为空")
    private String category;

    private Integer status = 1;
}