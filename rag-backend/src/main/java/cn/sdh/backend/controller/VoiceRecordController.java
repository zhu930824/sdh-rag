package cn.sdh.backend.controller;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.entity.VoiceRecord;
import cn.sdh.backend.service.VoiceRecordService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/voice")
@RequiredArgsConstructor
public class VoiceRecordController {

    private final VoiceRecordService voiceRecordService;

    @GetMapping("/records")
    public Result<IPage<VoiceRecord>> getRecords(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }
        return Result.success(voiceRecordService.getUserRecords(userId, page, pageSize));
    }

    @GetMapping("/session/{sessionId}")
    public Result<List<VoiceRecord>> getSessionRecords(@PathVariable String sessionId) {
        return Result.success(voiceRecordService.getSessionRecords(sessionId));
    }

    @GetMapping("/{id}")
    public Result<VoiceRecord> getById(@PathVariable Long id) {
        VoiceRecord record = voiceRecordService.getById(id);
        if (record == null) {
            return Result.notFound("记录不存在");
        }
        return Result.success(record);
    }

    @PostMapping(value = "/ask", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<VoiceRecord> voiceAsk(
            @RequestParam("voice") MultipartFile voiceFile,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String textContent) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        try {
            String voiceUrl = saveVoiceFile(voiceFile);
            Integer duration = 0;

            VoiceRecord record = voiceRecordService.processVoiceQA(userId, sessionId, voiceUrl, duration, textContent);
            return Result.success(record);
        } catch (Exception e) {
            return Result.error("语音处理失败: " + e.getMessage());
        }
    }

    @PostMapping("/text-to-voice")
    public Result<VoiceRecord> textToVoice(
            @RequestParam String text,
            @RequestParam(required = false) String sessionId) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        try {
            VoiceRecord record = voiceRecordService.processVoiceQA(userId, sessionId, null, 0, text);
            return Result.success(record);
        } catch (Exception e) {
            return Result.error("处理失败: " + e.getMessage());
        }
    }

    @PostMapping("/transcribe")
    public Result<String> transcribe(@RequestParam("voice") MultipartFile voiceFile) {
        try {
            String voiceUrl = saveVoiceFile(voiceFile);
            String text = voiceRecordService.transcribeAudio(voiceUrl);
            return Result.success(text);
        } catch (Exception e) {
            return Result.error("语音识别失败: " + e.getMessage());
        }
    }

    @GetMapping("/synthesize")
    public Result<byte[]> synthesize(@RequestParam String text) {
        byte[] audio = voiceRecordService.synthesizeSpeech(text);
        return Result.success(audio);
    }

    @GetMapping("/stats")
    public Result<Integer> getStats() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }
        return Result.success(voiceRecordService.getUserSuccessCount(userId));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        VoiceRecord record = voiceRecordService.getById(id);
        if (record != null && userId.equals(record.getUserId())) {
            voiceRecordService.removeById(id);
        }
        return Result.success();
    }

    private String saveVoiceFile(MultipartFile file) throws Exception {
        String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        return "/uploads/voice/" + fileName;
    }
}
