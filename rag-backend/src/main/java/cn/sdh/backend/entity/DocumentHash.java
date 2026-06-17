package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("document_hash")
public class DocumentHash implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("document_id")
    private Long documentId;

    @TableField("knowledge_id")
    private Long knowledgeId;

    @TableField("content_md5")
    private String contentMd5;

    @TableField("minhash_signature")
    private String minhashSignature;

    @TableField("create_time")
    private LocalDateTime createTime;
}
