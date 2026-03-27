package cn.sdh.backend.service;

import cn.sdh.backend.entity.Tag;
import cn.sdh.backend.entity.DocumentTag;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface TagService extends IService<Tag> {

    IPage<Tag> getPage(Integer page, Integer pageSize, String keyword, String category);

    List<Tag> getAllTags();

    void addDocumentTag(Long documentId, Long tagId, String source, Long userId);

    void removeDocumentTag(Long documentId, Long tagId);

    List<Tag> getDocumentTags(Long documentId);

    Tag getByName(String name);
}