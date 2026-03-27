package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("embed_config")
public class EmbedConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank(message = "配置名称不能为空")
    @Size(max = 100, message = "配置名称不能超过100个字符")
    private String name;

    private String theme;

    private String position;

    private Integer width;

    private Integer height;

    private String primaryColor;

    @Size(max = 100, message = "标题不能超过100个字符")
    private String title;

    @Size(max = 255, message = "欢迎语不能超过255个字符")
    private String welcomeMsg;

    private String knowledgeIds;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}