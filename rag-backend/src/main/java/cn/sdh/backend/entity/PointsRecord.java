package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("points_record")
public class PointsRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Integer points;

    private Integer balance;

    private String type;

    private String description;

    private String relatedType;

    private Long relatedId;

    private LocalDateTime createTime;
}