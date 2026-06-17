package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.DocumentHash;
import cn.sdh.backend.mapper.DocumentHashMapper;
import cn.sdh.backend.service.DocumentDedupService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentDedupServiceImpl implements DocumentDedupService {

    private final DocumentHashMapper documentHashMapper;
    private final ObjectMapper objectMapper;

    private static final int MINHASH_SEED_COUNT = 128;

    @Override
    public Long checkMd5Duplicate(Long knowledgeId, String contentMd5) {
        DocumentHash existing = documentHashMapper.findByKnowledgeAndMd5(knowledgeId, contentMd5);
        if (existing != null) {
            log.info("发现MD5重复文档: knowledgeId={}, existingDocId={}", knowledgeId, existing.getDocumentId());
            return existing.getDocumentId();
        }
        return null;
    }

    @Override
    public SimilarDocResult checkMinHashSimilarity(Long knowledgeId, String content, double threshold) {
        int[] querySignature = computeMinHashSignature(content);
        List<DocumentHash> allHashes = documentHashMapper.findByKnowledgeId(knowledgeId);

        double bestSimilarity = 0;
        Long bestDocId = null;

        for (DocumentHash docHash : allHashes) {
            if (docHash.getMinhashSignature() == null) continue;
            try {
                int[] existingSignature = objectMapper.readValue(
                    docHash.getMinhashSignature(), int[].class);
                double similarity = jaccardSimilarity(querySignature, existingSignature);
                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity;
                    bestDocId = docHash.getDocumentId();
                }
            } catch (JsonProcessingException e) {
                log.warn("MinHash签名解析失败: docId={}", docHash.getDocumentId());
            }
        }

        if (bestSimilarity >= threshold) {
            log.info("发现近似重复文档: knowledgeId={}, existingDocId={}, similarity={}",
                knowledgeId, bestDocId, bestSimilarity);
            return new SimilarDocResult(bestDocId, bestSimilarity);
        }
        return null;
    }

    @Override
    public void saveDocumentHash(Long documentId, Long knowledgeId, String contentMd5, String content) {
        DocumentHash hash = new DocumentHash();
        hash.setDocumentId(documentId);
        hash.setKnowledgeId(knowledgeId);
        hash.setContentMd5(contentMd5);
        try {
            hash.setMinhashSignature(
                objectMapper.writeValueAsString(computeMinHashSignature(content)));
        } catch (JsonProcessingException e) {
            log.error("MinHash签名序列化失败", e);
        }
        hash.setCreateTime(java.time.LocalDateTime.now());
        documentHashMapper.insert(hash);
    }

    private int[] computeMinHashSignature(String content) {
        Set<String> shingles = new HashSet<>();
        for (int i = 0; i < content.length() - 2; i++) {
            shingles.add(content.substring(i, i + 3));
        }
        String[] shingleArray = shingles.toArray(new String[0]);

        int[] signature = new int[MINHASH_SEED_COUNT];
        Arrays.fill(signature, Integer.MAX_VALUE);

        for (int seed = 0; seed < MINHASH_SEED_COUNT; seed++) {
            for (String shingle : shingleArray) {
                int hash = hashWithSeed(shingle, seed);
                if (hash < signature[seed]) {
                    signature[seed] = hash;
                }
            }
        }
        return signature;
    }

    private int hashWithSeed(String str, int seed) {
        int hash = seed;
        for (char c : str.toCharArray()) {
            hash = 31 * hash + c;
        }
        return hash & Integer.MAX_VALUE;
    }

    private double jaccardSimilarity(int[] sig1, int[] sig2) {
        if (sig1.length != sig2.length) return 0;
        int match = 0;
        for (int i = 0; i < sig1.length; i++) {
            if (sig1[i] == sig2[i]) match++;
        }
        return (double) match / sig1.length;
    }
}
