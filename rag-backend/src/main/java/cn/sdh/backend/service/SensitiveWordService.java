package cn.sdh.backend.service;

import cn.sdh.backend.entity.SensitiveWord;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

public interface SensitiveWordService {

    IPage<SensitiveWord> getPage(Integer page, Integer pageSize, String keyword, String category);

    SensitiveWord getById(Long id);

    boolean save(SensitiveWord sensitiveWord);

    void removeById(Long id);

    void removeByIds(List<Long> ids);

    void deleteById(Long id);

    void deleteBatch(List<Long> ids);

    void toggleStatus(Long id);

    boolean containsSensitiveWord(String text);

    List<String> findSensitiveWords(String text);
}