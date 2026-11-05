package cn.sdh.backend.service;

import cn.sdh.backend.common.exception.EvaluationIntegrityException;
import cn.sdh.backend.entity.ModelConfig;
import cn.sdh.backend.utils.ContentFingerprint;
import com.alibaba.fastjson2.JSON;
import java.util.*;

/** Private configuration copies and client instances live only for one synchronous execution. */
public final class ModelExecutionScope implements AutoCloseable {
    private static final ThreadLocal<ModelExecutionScope> CURRENT=new ThreadLocal<>();
    private final List<ModelConfig> catalog;
    private final Map<String,Object> clients=new HashMap<>();
    private final Map<String,Map<String,Object>> observed=new TreeMap<>();
    private boolean failed,closed;
    public ModelExecutionScope(List<ModelConfig> catalog) {
        if(CURRENT.get()!=null) throw new IllegalStateException("模型执行作用域不能重叠");
        this.catalog=catalog.stream().map(ModelExecutionScope::copy).toList(); CURRENT.set(this);
    }
    public static boolean active() { return CURRENT.get()!=null; }
    public static void failIfActive() { if(active()) CURRENT.get().failed=true; }
    public static void checkIfActive() { if(active()) CURRENT.get().check(); }
    @SuppressWarnings("unchecked") static <M> M resolve(ModelFactory<M> factory,String selector,boolean byId) {
        var scope=CURRENT.get(); scope.check();
        String key=JSON.toJSONString(Arrays.asList(factory.getClass().getName(),factory.getModelType(),selector,byId));
        if(scope.clients.containsKey(key)) return (M)scope.clients.get(key);
        try {
            var available=scope.catalog.stream().filter(c -> factory.getModelType().equals(c.getModelType())).toList();
            List<ModelConfig> matches;
            if(selector==null || selector.isBlank()) {
                matches=available.stream().filter(c -> Integer.valueOf(1).equals(c.getIsDefault())).toList();
                if(matches.isEmpty()) matches=available.stream().filter(c -> factory.getDefaultModelName().equals(c.getModelId())).toList();
            } else {
                matches=available.stream().filter(c -> selector.equals(c.getModelId())).toList();
                if(matches.isEmpty() && !byId) matches=available.stream().filter(c -> selector.equals(c.getName())).toList();
            }
            if(matches.size()!=1 || !Integer.valueOf(1).equals(matches.getFirst().getStatus())) throw new EvaluationIntegrityException();
            ModelConfig effective=factory.executionConfiguration(copy(matches.getFirst()));
            M model=factory.createExecutionModel(copy(effective));
            if(model==null) throw new EvaluationIntegrityException();
            var descriptor=factory.executionDescriptor(effective,selector);
            descriptor.put("clientClass",model.getClass().getName());
            descriptor.put("clientVersion",model.getClass().getPackage().getImplementationVersion());
            descriptor.put("clientSha256",ContentFingerprint.classSha256(model.getClass()));
            scope.observed.put(key,Collections.unmodifiableMap(descriptor)); scope.clients.put(key,model);
            return model;
        } catch(RuntimeException invalid) { scope.failed=true; throw new EvaluationIntegrityException(); }
    }
    public String catalogSha256() {
        var redacted=catalog.stream().map(c -> {
            var values=JSON.parseObject(JSON.toJSONString(c)); values.remove("apiKey");
            values.put("baseUrl",ContentFingerprint.sha256(c.getBaseUrl())); return values;
        }).sorted(Comparator.comparing(JSON::toJSONString)).toList();
        return ContentFingerprint.sha256(JSON.toJSONString(redacted));
    }
    public List<Map<String,Object>> bindings() { return List.copyOf(observed.values()); }
    public String bindingsSha256() { return ContentFingerprint.sha256(JSON.toJSONString(bindings())); }
    public void check() { if(closed || failed) throw new EvaluationIntegrityException(); }
    public static ModelConfig copy(ModelConfig original) { var c=new ModelConfig(); org.springframework.beans.BeanUtils.copyProperties(original,c); return c; }
    @Override public void close() { if(!closed) { closed=true; CURRENT.remove(); clients.clear(); } }
    @Override public String toString() { return "ModelExecutionScope[bindings="+observed.size()+"]"; }
}
