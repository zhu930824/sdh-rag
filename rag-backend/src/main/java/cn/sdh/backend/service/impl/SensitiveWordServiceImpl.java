package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.SensitiveWord;
import cn.sdh.backend.mapper.SensitiveWordMapper;
import cn.sdh.backend.service.SensitiveWordService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class SensitiveWordServiceImpl extends ServiceImpl<SensitiveWordMapper, SensitiveWord> implements SensitiveWordService {

    @Override
    public IPage<SensitiveWord> getPage(Integer page, Integer pageSize, String keyword, String category) {
        Page<SensitiveWord> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<SensitiveWord> wrapper = new LambdaQueryWrapper<>();
        
        if (StringUtils.hasText(keyword)) {
            wrapper.like(SensitiveWord::getWord, keyword);
        }
        if (StringUtils.hasText(category)) {
            wrapper.eq(SensitiveWord::getCategory, category);
        }
        wrapper.orderByDesc(SensitiveWord::getCreateTime);
        
        return page(pageParam, wrapper);
    }

    @Override
    public boolean containsSensitiveWord(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        
        List<SensitiveWord> words = list(new LambdaQueryWrapper<SensitiveWord>()
                .eq(SensitiveWord::getStatus, 1));
        
        for (SensitiveWord sw : words) {
            if (text.contains(sw.getWord())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> findSensitiveWords(String text) {
        List<String> found = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return found;
        }

        List<SensitiveWord> words = list(new LambdaQueryWrapper<SensitiveWord>()
                .eq(SensitiveWord::getStatus, 1));

        for (SensitiveWord sw : words) {
            if (text.contains(sw.getWord())) {
                found.add(sw.getWord());
            }
        }
        return found;
    }

    @Override
    public cn.sdh.backend.entity.SensitiveWord getById(Long id) {
        return super.getById(id);
    }

    @Override
    public boolean save(SensitiveWord sensitiveWord) {
        return super.save(sensitiveWord);
    }

    @Override
    public void removeById(Long id) {
        super.removeById(id);
    }

    @Override
    public void removeByIds(List<Long> ids) {
        super.removeByIds(ids);
    }

    @Override
    public void deleteById(Long id) {
        super.removeById(id);
    }

    @Override
    public void deleteBatch(List<Long> ids) {
        super.removeByIds(ids);
    }

    @Override
    public void toggleStatus(Long id) {
        SensitiveWord word = getById(id);
        if (word != null) {
            word.setStatus(word.getStatus() == 1 ? 0 : 1);
            super.updateById(word);
        }
    }
}