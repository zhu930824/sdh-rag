package cn.sdh.backend.service;

import cn.sdh.backend.entity.VoiceRecord;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface VoiceRecordService extends IService<VoiceRecord> {

    IPage<VoiceRecord> getUserRecords(Long userId, Integer page, Integer pageSize);

    List<VoiceRecord> getSessionRecords(String sessionId);

    VoiceRecord processVoiceQA(Long userId, String sessionId, String voiceUrl, Integer voiceDuration, String textContent);

    void setAnswer(Long recordId, String answerText, String answerVoiceUrl);

    int getUserSuccessCount(Long userId);

    String transcribeAudio(String audioUrl);

    byte[] synthesizeSpeech(String text);
}
