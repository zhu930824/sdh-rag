package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("stats_daily")
public class StatsDaily {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate statDate;

    private String statType;

    private String metricName;

    private Long metricValue;

    private String extraData;

    private LocalDateTime createTime;
}