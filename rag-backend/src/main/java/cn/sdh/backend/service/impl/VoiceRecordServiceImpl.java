package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.VoiceRecord;
import cn.sdh.backend.mapper.VoiceRecordMapper;
import cn.sdh.backend.service.VoiceRecordService;
import cn.sdh.backend.service.ChatService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceRecordServiceImpl extends ServiceImpl<VoiceRecordMapper, VoiceRecord> implements VoiceRecordService {

    private final VoiceRecordMapper voiceRecordMapper;
    private final ChatService chatService;

    @Override
    public IPage<VoiceRecord> getUserRecords(Long userId, Integer page, Integer pageSize) {
        Page<VoiceRecord> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<VoiceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VoiceRecord::getUserId, userId)
               .orderByDesc(VoiceRecord::getCreateTime);
        return page(pageParam, wrapper);
    }

    @Override
    public List<VoiceRecord> getSessionRecords(String sessionId) {
        return voiceRecordMapper.selectBySessionId(sessionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VoiceRecord processVoiceQA(Long userId, String sessionId, String voiceUrl, Integer voiceDuration, String textContent) {
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }

        VoiceRecord record = new VoiceRecord();
        record.setUserId(userId);
        record.setSessionId(sessionId);
        record.setVoiceUrl(voiceUrl);
        record.setVoiceDuration(voiceDuration);
        record.setTextContent(textContent);
        record.setStatus((byte) 0);
        record.setCreateTime(LocalDateTime.now());
        save(record);

        try {
            String answer = chatService.chat(userId, textContent, sessionId);
            record.setAnswerText(answer);
            record.setStatus((byte) 1);
            updateById(record);
        } catch (Exception e) {
            log.error("语音问答处理失败: {}", e.getMessage());
            record.setStatus((byte) 0);
            updateById(record);
        }

        return record;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setAnswer(Long recordId, String answerText, String answerVoiceUrl) {
        VoiceRecord record = getById(recordId);
        if (record != null) {
            record.setAnswerText(answerText);
            record.setAnswerVoiceUrl(answerVoiceUrl);
            record.setStatus((byte) 1);
            updateById(record);
        }
    }

    @Override
    public int getUserSuccessCount(Long userId) {
        return voiceRecordMapper.countSuccessByUserId(userId);
    }

    @Override
    public String transcribeAudio(String audioUrl) {
        log.info("转录音频: {}", audioUrl);
        return "音频转录功能需要集成ASR服务";
    }

    @Override
    public byte[] synthesizeSpeech(String text) {
        log.info("合成语音, 文本长度: {}", text.length());
        return new byte[0];
    }
}
