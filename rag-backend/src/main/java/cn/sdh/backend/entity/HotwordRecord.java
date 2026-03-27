package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("hotword_record")
public class HotwordRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String word;

    private Integer count;

    private LocalDate queryDate;

    private Long userId;

    private String sessionId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}