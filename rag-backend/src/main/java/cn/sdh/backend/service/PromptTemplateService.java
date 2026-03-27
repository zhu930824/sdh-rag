package cn.sdh.backend.service;

import cn.sdh.backend.entity.PromptTemplate;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

public interface PromptTemplateService extends IService<PromptTemplate> {

    IPage<PromptTemplate> getPage(Integer page, Integer pageSize, String category, String keyword);

    List<PromptTemplate> getActiveTemplates();

    List<PromptTemplate> getByCategory(String category);

    PromptTemplate getByCode(String code);

    PromptTemplate createTemplate(PromptTemplate template, Long userId);

    void updateTemplate(PromptTemplate template);

    void deleteTemplate(Long id);

    String renderTemplate(String code, Map<String, String> variables);

    void incrementUseCount(Long id);

    List<String> getCategories();

    void validateTemplate(String content);
}
