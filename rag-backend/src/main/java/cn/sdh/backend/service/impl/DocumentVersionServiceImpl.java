package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.DocumentVersion;
import cn.sdh.backend.entity.KnowledgeDocument;
import cn.sdh.backend.mapper.DocumentVersionMapper;
import cn.sdh.backend.mapper.KnowledgeDocumentMapper;
import cn.sdh.backend.service.DocumentVersionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentVersionServiceImpl extends ServiceImpl<DocumentVersionMapper, DocumentVersion> implements DocumentVersionService {

    private final DocumentVersionMapper versionMapper;
    private final KnowledgeDocumentMapper documentMapper;

    @Override
    public List<DocumentVersion> getVersionsByDocumentId(Long documentId) {
        return versionMapper.selectByDocumentId(documentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentVersion createVersion(Long documentId, String changeSummary, Long userId) {
        KnowledgeDocument document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new RuntimeException("文档不存在");
        }

        Integer maxVersion = versionMapper.selectMaxVersion(documentId);
        int newVersionNumber = (maxVersion != null ? maxVersion : 0) + 1;

        DocumentVersion version = new DocumentVersion();
        version.setDocumentId(documentId);
        version.setVersionNumber(newVersionNumber);
        version.setContent(document.getContent());
        version.setChangeSummary(changeSummary);
        version.setUserId(userId);
        version.setStatus(1);
        version.setCreateTime(LocalDateTime.now());

        save(version);
        return version;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollbackToVersion(Long versionId, Long userId) {
        DocumentVersion version = getById(versionId);
        if (version == null) {
            throw new RuntimeException("版本不存在");
        }

        KnowledgeDocument document = documentMapper.selectById(version.getDocumentId());
        if (document == null) {
            throw new RuntimeException("文档不存在");
        }

        // 创建当前版本的备份
        createVersion(version.getDocumentId(), "回滚前自动备份", userId);

        // 恢复到指定版本
        document.setContent(version.getContent());
        document.setUpdateTime(LocalDateTime.now());
        documentMapper.updateById(document);
    }

    @Override
    public DocumentVersion getLatestVersion(Long documentId) {
        List<DocumentVersion> versions = versionMapper.selectByDocumentId(documentId);
        return versions.isEmpty() ? null : versions.get(0);
    }
}