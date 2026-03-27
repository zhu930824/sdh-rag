package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("document_process_task")
public class DocumentProcessTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;

    private String taskType;

    private Integer status;

    private Integer progress;

    private String result;

    private String errorMsg;

    private String processor;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime createTime;
}