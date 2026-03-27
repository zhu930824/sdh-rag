package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.PromptTemplate;
import cn.sdh.backend.mapper.PromptTemplateMapper;
import cn.sdh.backend.service.PromptTemplateService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PromptTemplateServiceImpl extends ServiceImpl<PromptTemplateMapper, PromptTemplate> implements PromptTemplateService {

    private final PromptTemplateMapper promptTemplateMapper;

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{([^}]+)}");

    @Override
    public IPage<PromptTemplate> getPage(Integer page, Integer pageSize, String category, String keyword) {
        Page<PromptTemplate> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<PromptTemplate> wrapper = new LambdaQueryWrapper<>();
        
        if (StringUtils.hasText(category)) {
            wrapper.eq(PromptTemplate::getCategory, category);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(PromptTemplate::getName, keyword)
                    .or().like(PromptTemplate::getDescription, keyword));
        }
        wrapper.orderByDesc(PromptTemplate::getUseCount)
               .orderByDesc(PromptTemplate::getCreateTime);
        
        return page(pageParam, wrapper);
    }

    @Override
    public List<PromptTemplate> getActiveTemplates() {
        return promptTemplateMapper.selectActiveTemplates();
    }

    @Override
    public List<PromptTemplate> getByCategory(String category) {
        return promptTemplateMapper.selectByCategory(category);
    }

    @Override
    public PromptTemplate getByCode(String code) {
        return promptTemplateMapper.selectByCode(code);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PromptTemplate createTemplate(PromptTemplate template, Long userId) {
        PromptTemplate existing = getByCode(template.getCode());
        if (existing != null) {
            throw new RuntimeException("模板编码已存在: " + template.getCode());
        }

        validateTemplate(template.getContent());
        
        template.setIsSystem((byte) 0);
        template.setStatus((byte) 1);
        template.setUseCount(0);
        template.setUserId(userId);
        template.setCreateTime(LocalDateTime.now());
        template.setUpdateTime(LocalDateTime.now());
        save(template);
        
        return template;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTemplate(PromptTemplate template) {
        PromptTemplate existing = getById(template.getId());
        if (existing == null) {
            throw new RuntimeException("模板不存在");
        }
        if (existing.getIsSystem() == 1) {
            throw new RuntimeException("系统模板不可修改");
        }

        if (!existing.getCode().equals(template.getCode())) {
            PromptTemplate codeCheck = getByCode(template.getCode());
            if (codeCheck != null) {
                throw new RuntimeException("模板编码已存在: " + template.getCode());
            }
        }

        validateTemplate(template.getContent());
        
        template.setUpdateTime(LocalDateTime.now());
        updateById(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTemplate(Long id) {
        PromptTemplate template = getById(id);
        if (template == null) {
            throw new RuntimeException("模板不存在");
        }
        if (template.getIsSystem() == 1) {
            throw new RuntimeException("系统模板不可删除");
        }
        removeById(id);
    }

    @Override
    public String renderTemplate(String code, Map<String, String> variables) {
        PromptTemplate template = getByCode(code);
        if (template == null) {
            throw new RuntimeException("模板不存在: " + code);
        }

        String content = template.getContent();
        if (variables != null) {
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                content = content.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }

        incrementUseCount(template.getId());
        return content;
    }

    @Override
    public void incrementUseCount(Long id) {
        promptTemplateMapper.incrementUseCount(id);
    }

    @Override
    public List<String> getCategories() {
        return Arrays.asList("general", "qa", "chat", "summary", "translate", "custom");
    }

    @Override
    public void validateTemplate(String content) {
        if (!StringUtils.hasText(content)) {
            throw new RuntimeException("模板内容不能为空");
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(content);
        Set<String> variables = new HashSet<>();
        while (matcher.find()) {
            variables.add(matcher.group(1));
        }

        if (variables.isEmpty()) {
            throw new RuntimeException("模板必须包含至少一个变量占位符，如 {question}");
        }
    }
}
