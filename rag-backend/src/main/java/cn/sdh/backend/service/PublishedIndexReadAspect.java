package cn.sdh.backend.service;

import cn.sdh.backend.dto.SearchResult;
import cn.sdh.backend.entity.KnowledgeBase;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

/** One immutable view covers cascade stages and nested vector/keyword/summary queries. */
@Aspect @Component
public class PublishedIndexReadAspect {
    private final PublishedIndexService publications;
    public PublishedIndexReadAspect(PublishedIndexService publications) { this.publications = publications; }
    @Around("execution(public * cn.sdh.backend.service.impl.RagSearchServiceImpl.search*(..)) || " +
            "execution(public * cn.sdh.backend.service.impl.VectorStoreServiceImpl.*Search(..)) || " +
            "execution(public * cn.sdh.backend.service.impl.VectorStoreServiceImpl.searchByDocumentIds(..)) || " +
            "execution(public * cn.sdh.backend.service.impl.VectorStoreServiceImpl.*PublishedChunks(..)) || " +
            "execution(public * cn.sdh.backend.service.impl.SummaryIndexServiceImpl.coarseRetrieval(..))")
    public Object read(ProceedingJoinPoint call) throws Throwable {
        Object[] args = call.getArgs();
        Object knowledge=call.getSignature().getName().endsWith("PublishedChunks")?args[0]:args[1] instanceof java.util.Set?args[2]:args[1];
        Long id = knowledge instanceof KnowledgeBase base ? base.getId() : (Long)knowledge;
        try (var view = publications.open(id)) {
            Object result = call.proceed();
            view.checkActive(); // Reject late responses even if an ES call crossed the absolute lease deadline.
            if (result instanceof SearchResult search) search.setPublicationSnapshot(view.snapshot());
            if (result instanceof RagSearchService.RagSearchResult search) search.setPublicationSnapshot(view.snapshot());
            return result;
        }
    }
}
