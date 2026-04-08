package cn.sdh.backend.service;

import cn.sdh.backend.entity.Announcement;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface AnnouncementService extends IService<Announcement> {

    IPage<Announcement> getPage(Integer page, Integer pageSize, String type, Integer status);

    List<Announcement> getActiveAnnouncements();

    void publish(Long id, Long publisherId);

    void offline(Long id);





}