package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("document_version")
public class DocumentVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;

    private Integer versionNumber;

    private String content;

    private String changeSummary;

    private Long userId;

    private Integer status;

    private LocalDateTime createTime;
}