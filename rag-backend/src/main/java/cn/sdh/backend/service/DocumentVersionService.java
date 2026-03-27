package cn.sdh.backend.service;

import cn.sdh.backend.entity.DocumentVersion;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface DocumentVersionService extends IService<DocumentVersion> {

    List<DocumentVersion> getVersionsByDocumentId(Long documentId);

    DocumentVersion createVersion(Long documentId, String changeSummary, Long userId);

    void rollbackToVersion(Long versionId, Long userId);

    DocumentVersion getLatestVersion(Long documentId);
}