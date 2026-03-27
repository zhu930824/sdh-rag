package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("voice_record")
public class VoiceRecord implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("session_id")
    private String sessionId;

    @TableField("voice_url")
    private String voiceUrl;

    @TableField("voice_duration")
    private Integer voiceDuration;

    @TableField("text_content")
    private String textContent;

    @TableField("answer_text")
    private String answerText;

    @TableField("answer_voice_url")
    private String answerVoiceUrl;

    private Byte status;

    @TableField("create_time")
    private LocalDateTime createTime;
}
