package cn.sdh.backend.service;

import cn.sdh.backend.entity.ModelConfig;
import cn.sdh.backend.mapper.ModelConfigMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ModelFactoryTest {
    static final class Factory extends ModelFactory<ModelConfig> {
        Factory(ModelConfigMapper mapper) { super(mapper); }
        @Override protected ModelConfig createModel(ModelConfig config) { return config; }
        @Override protected ModelConfig createDefaultModel(String selector) { return null; }
        @Override protected String getDefaultModelName() { return "default"; }
        @Override protected String getModelType() { return "embedding"; }
    }
    static ModelConfig config(String model) {
        var c=new ModelConfig(); c.setId(1L); c.setName("alias"); c.setModelId(model); c.setModelType("embedding");
        c.setProvider("openai"); c.setStatus(1); c.setApiKey("private-key"); c.setBaseUrl("https://private.example"); return c;
    }
    @Test void changingAnAliasInvalidatesTheClientWithoutMutatingThePreviousBinding() {
        var mapper=mock(ModelConfigMapper.class); var original=config("model-a");
        when(mapper.selectOne(any())).thenReturn(original);
        var factory=new Factory(mapper); var first=factory.getModel("alias");
        original.setModelId("model-b");
        var second=factory.getModel("alias");
        assertEquals("model-a",first.getModelId(),"A binding must own its configuration copy");
        assertEquals("model-b",second.getModelId(),"A name cache must not hide the new configuration");
        assertNotSame(first,second);
    }
    @Test void executionPinsCatalogAndClientsAndNeverPersistsCredentials() {
        var mapper=mock(ModelConfigMapper.class); var original=config("model-a"); var factory=new Factory(mapper);
        try(var scope=new ModelExecutionScope(java.util.List.of(original))) {
            original.setModelId("model-b");
            var first=factory.getModel("alias"); factory.clearCache();
            assertEquals("model-a",first.getModelId()); assertSame(first,factory.getModel("alias"));
            String json=com.alibaba.fastjson2.JSON.toJSONString(scope.bindings());
            assertFalse(json.contains("private-key")); assertFalse(json.contains("https://private.example"));
            assertEquals(64,scope.bindingsSha256().length()); verifyNoInteractions(mapper);
        }
        assertFalse(ModelExecutionScope.active());
    }
    @Test void ambiguousOrDisabledModelsPoisonTheExecutionInsteadOfFallingBack() {
        var mapper=mock(ModelConfigMapper.class); var factory=new Factory(mapper);
        var a=config("model-a"); var b=config("model-b"); b.setId(2L);
        try(var scope=new ModelExecutionScope(java.util.List.of(a,b))) {
            assertThrows(cn.sdh.backend.common.exception.EvaluationIntegrityException.class,() -> factory.getModel("alias"));
            assertThrows(cn.sdh.backend.common.exception.EvaluationIntegrityException.class,scope::check);
        }
        a.setStatus(0);
        try(var scope=new ModelExecutionScope(java.util.List.of(a))) {
            assertThrows(cn.sdh.backend.common.exception.EvaluationIntegrityException.class,() -> factory.getModel("model-a"));
        }
    }
    @Test void aLiteralDefaultSelectorCannotAliasTheImplicitDefaultBinding() {
        var factory=new Factory(mock(ModelConfigMapper.class));
        var implicit=config("model-a"); implicit.setIsDefault(1);
        var literal=config("DEFAULT"); literal.setId(2L); literal.setIsDefault(0);
        try(var scope=new ModelExecutionScope(java.util.List.of(implicit,literal))) {
            assertEquals("model-a",factory.getModel(null).getModelId());
            assertEquals("DEFAULT",factory.getModel("DEFAULT").getModelId());
            assertEquals(2,scope.bindings().size());
        }
    }
}
