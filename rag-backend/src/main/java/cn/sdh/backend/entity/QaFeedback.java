package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("qa_feedback")
public class QaFeedback {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long chatHistoryId;

    private Long userId;

    private Integer rating;

    private String feedbackType;

    private String comment;

    private String correctAnswer;

    private Integer status;

    private Long handledBy;

    private LocalDateTime handleTime;

    private LocalDateTime createTime;
}