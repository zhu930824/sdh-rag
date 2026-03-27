package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.AiAssistant;
import cn.sdh.backend.entity.AssistantRating;
import cn.sdh.backend.mapper.AiAssistantMapper;
import cn.sdh.backend.mapper.AssistantRatingMapper;
import cn.sdh.backend.service.AiAssistantService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiAssistantServiceImpl extends ServiceImpl<AiAssistantMapper, AiAssistant> implements AiAssistantService {

    private final AiAssistantMapper assistantMapper;
    private final AssistantRatingMapper ratingMapper;

    @Override
    public IPage<AiAssistant> getPage(Integer page, Integer pageSize, String keyword, String category) {
        Page<AiAssistant> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<AiAssistant> wrapper = new LambdaQueryWrapper<>();
        
        if (StringUtils.hasText(keyword)) {
            wrapper.like(AiAssistant::getName, keyword)
                   .or()
                   .like(AiAssistant::getDescription, keyword);
        }
        if (StringUtils.hasText(category)) {
            wrapper.eq(AiAssistant::getCategory, category);
        }
        wrapper.orderByDesc(AiAssistant::getSort)
               .orderByDesc(AiAssistant::getUseCount);
        
        return page(pageParam, wrapper);
    }

    @Override
    public IPage<AiAssistant> getPublicAssistants(Integer page, Integer pageSize, String category) {
        Page<AiAssistant> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<AiAssistant> wrapper = new LambdaQueryWrapper<>();
        
        wrapper.eq(AiAssistant::getIsPublic, 1)
               .eq(AiAssistant::getStatus, 1);
        
        if (StringUtils.hasText(category)) {
            wrapper.eq(AiAssistant::getCategory, category);
        }
        wrapper.orderByDesc(AiAssistant::getSort)
               .orderByDesc(AiAssistant::getUseCount);
        
        return page(pageParam, wrapper);
    }

    @Override
    public AiAssistant getDetail(Long id) {
        return getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiAssistant create(AiAssistant assistant) {
        assistant.setUseCount(0);
        assistant.setRatingAvg(BigDecimal.ZERO);
        assistant.setRatingCount(0);
        if (assistant.getStatus() == null) assistant.setStatus(1);
        if (assistant.getIsPublic() == null) assistant.setIsPublic(1);
        save(assistant);
        return assistant;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiAssistant update(Long id, AiAssistant assistant) {
        assistant.setId(id);
        updateById(assistant);
        return assistant;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void incrementUseCount(Long id) {
        AiAssistant assistant = getById(id);
        if (assistant != null) {
            assistant.setUseCount(assistant.getUseCount() + 1);
            updateById(assistant);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRating(Long id) {
        LambdaQueryWrapper<AssistantRating> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AssistantRating::getAssistantId, id);
        List<AssistantRating> ratings = ratingMapper.selectList(wrapper);
        
        if (!ratings.isEmpty()) {
            double avg = ratings.stream()
                .mapToInt(AssistantRating::getRating)
                .average()
                .orElse(0);
            
            AiAssistant assistant = getById(id);
            if (assistant != null) {
                assistant.setRatingAvg(BigDecimal.valueOf(avg));
                assistant.setRatingCount(ratings.size());
                updateById(assistant);
            }
        }
    }

    @Override
    public List<AiAssistant> getHotAssistants(int limit) {
        LambdaQueryWrapper<AiAssistant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiAssistant::getIsPublic, 1)
               .eq(AiAssistant::getStatus, 1)
               .orderByDesc(AiAssistant::getUseCount)
               .last("LIMIT " + limit);
        return list(wrapper);
    }

    @Override
    public List<String> getCategories() {
        return list().stream()
            .map(AiAssistant::getCategory)
            .distinct()
            .collect(Collectors.toList());
    }
}