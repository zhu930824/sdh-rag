package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("document_tag")
public class DocumentTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;

    private Long tagId;

    private String source;

    private BigDecimal confidence;

    private Long userId;

    private LocalDateTime createTime;
}