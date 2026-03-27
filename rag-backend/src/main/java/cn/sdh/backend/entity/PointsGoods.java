package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("points_goods")
public class PointsGoods {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private String image;

    private Integer points;

    private Integer stock;

    private String type;

    private Integer status;

    private Integer sort;

    private LocalDateTime createTime;
}