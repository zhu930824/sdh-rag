package cn.sdh.backend.service;

import cn.sdh.backend.entity.AiAssistant;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface AiAssistantService extends IService<AiAssistant> {

    IPage<AiAssistant> getPage(Integer page, Integer pageSize, String keyword, String category);

    IPage<AiAssistant> getPublicAssistants(Integer page, Integer pageSize, String category);

    AiAssistant getDetail(Long id);

    AiAssistant create(AiAssistant assistant);

    AiAssistant update(Long id, AiAssistant assistant);

    void delete(Long id);

    void incrementUseCount(Long id);

    void updateRating(Long id);

    List<AiAssistant> getHotAssistants(int limit);

    List<String> getCategories();
}