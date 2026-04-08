package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.Announcement;
import cn.sdh.backend.mapper.AnnouncementMapper;
import cn.sdh.backend.service.AnnouncementService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnnouncementServiceImpl extends ServiceImpl<AnnouncementMapper, Announcement> implements AnnouncementService {

    private final AnnouncementMapper announcementMapper;


    @Override
    public IPage<Announcement> getPage(Integer page, Integer pageSize, String type, Integer status) {
        Page<Announcement> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<Announcement> wrapper = new LambdaQueryWrapper<>();
        
        if (StringUtils.hasText(type)) {
            wrapper.eq(Announcement::getType, type);
        }
        if (status != null) {
            wrapper.eq(Announcement::getStatus, status);
        }
        wrapper.orderByDesc(Announcement::getIsTop)
               .orderByDesc(Announcement::getPriority)
               .orderByDesc(Announcement::getCreateTime);
        
        return page(pageParam, wrapper);
    }

    @Override
    public List<Announcement> getActiveAnnouncements() {
        return announcementMapper.selectActive();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id, Long publisherId) {
        Announcement announcement = getById(id);
        if (announcement != null) {
            announcement.setStatus(1);
            announcement.setPublishTime(LocalDateTime.now());
            announcement.setPublisherId(publisherId);
            updateById(announcement);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void offline(Long id) {
        Announcement announcement = getById(id);
        if (announcement != null) {
            announcement.setStatus(2);
            updateById(announcement);
        }
    }


}