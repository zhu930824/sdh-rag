package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("assistant_rating")
public class AssistantRating {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long assistantId;

    private Long userId;

    private Integer rating;

    private String comment;

    private LocalDateTime createTime;
}