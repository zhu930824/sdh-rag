package cn.sdh.backend.service;

import cn.sdh.backend.common.exception.BusinessException;
import com.alibaba.fastjson2.JSON;
import java.util.*;

/** Canonical source bytes and strict regression inputs shared by approval and indexing. */
public final class KnowledgePublicationContent {
    public static final int MAX_SOURCE_BYTES=1_048_576;
    private KnowledgePublicationContent() {}
    public static String markdown(String title,String content) { return "# " + title + "\n\n" + content.trim() + "\n"; }
    public static List<String> queries(String json) {
        try {
            var raw=JSON.parseArray(json);
            if(raw==null || raw.isEmpty() || raw.size()>10) throw new IllegalArgumentException();
            List<String> queries=new ArrayList<>();
            for(Object value:raw) {
                if(!(value instanceof String text) || text.isBlank() || text.length()>500) throw new IllegalArgumentException();
                queries.add(text.trim());
            }
            if(new HashSet<>(queries).size()!=queries.size()) throw new IllegalArgumentException();
            return List.copyOf(queries);
        } catch(Exception invalid) { throw new BusinessException(400,"发布须配置 1—10 个有效回归问题，每个不超过 500 字符"); }
    }
}
