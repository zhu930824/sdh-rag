package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 知识库文档实体
 */
@Data
@TableName("knowledge_document")
public class KnowledgeDocument implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    /**
     * 文件类型：pdf、doc、docx、txt等
     */
    @TableField("file_type")
    private String fileType;

    /**
     * 文件存储路径（MinIO对象路径）
     */
    @TableField("file_path")
    private String filePath;

    /**
     * 文件大小（字节）
     */
    @TableField("file_size")
    private Long fileSize;

    /**
     * 分类ID
     */
    @TableField("category_id")
    private Long categoryId;

    /**
     * 上传用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 状态：0-处理中，1-成功，2-失败
     */
    private Integer status;

    // ==================== RAG 优化新增元数据字段 ====================

    /** 部门 */
    private String department;

    /** 文档类型: policy/manual/contract/report/faq */
    @TableField("doc_type")
    private String docType;

    /** 文档日期 */
    @TableField("doc_date")
    private LocalDate docDate;

    /** 新鲜度评分 0-1（入库时自动计算） */
    @TableField("freshness_score")
    private Double freshnessScore;

    /** 内容 MD5 哈希 */
    @TableField("content_md5")
    private String contentMd5;

    /** 质量状态: 0=正常 1=低质 2=疑似重复 */
    @TableField("quality_status")
    private Integer qualityStatus;

    @TableField("create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @TableField("update_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
