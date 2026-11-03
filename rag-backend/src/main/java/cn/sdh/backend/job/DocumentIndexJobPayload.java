package cn.sdh.backend.job;

import cn.sdh.backend.dto.DocumentLinkConfig;

/** Immutable snapshot used by a reliable document indexing job. */
public record DocumentIndexJobPayload(Long relationId, Long documentId, Long knowledgeId,
                                      Integer revision, DocumentLinkConfig config) {
}
