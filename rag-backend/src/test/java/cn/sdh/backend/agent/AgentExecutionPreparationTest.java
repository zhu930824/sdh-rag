package cn.sdh.backend.agent;

import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.entity.*;
import cn.sdh.backend.service.ModelConfigService;
import cn.sdh.backend.service.factory.ChatModelFactory;
import cn.sdh.backend.service.impl.ChatServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AgentExecutionPreparationTest {
    private final ModelConfigService configs = mock(ModelConfigService.class);
    private final ChatModelFactory models = mock(ChatModelFactory.class);
    private final ChatServiceImpl chat = mock(ChatServiceImpl.class);
    private final AgentExecutionPreparation preparation = new AgentExecutionPreparation(configs, models, new AgentCapability("openai"), chat);

    @Test void initialAndResumeBindIndependentCopiesOfTheExactConfiguration() {
        ModelConfig config = config("openai");
        when(configs.getById(1L)).thenReturn(config);
        var exact = mock(ChatModel.class); when(models.createConfiguredModel(any())).thenReturn(exact);
        for (var purpose : AgentExecutionPreparation.Purpose.values()) {
            var binding = preparation.model(1L, purpose);
            assertNotSame(config, binding.config()); assertEquals(config, binding.config()); assertSame(exact, binding.model());
            config.setApiKey("rotated-secret");
            assertNotEquals("rotated-secret", binding.config().getApiKey());
            config.setApiKey(null);
            var externalCopy = binding.config(); externalCopy.setModelId("changed");
            assertEquals("test-model", binding.config().getModelId());
            assertFalse(binding.toString().contains("apiKey"));
        }
        verify(models, never()).getModelById(any()); verify(models, never()).getModel(any());
    }
    @Test void unsupportedProviderIsRejectedBeforeAnyModelIsCreated() {
        when(configs.getById(1L)).thenReturn(config("deepseek"));
        for (var purpose : AgentExecutionPreparation.Purpose.values()) {
            assertEquals(400, assertThrows(BusinessException.class, () -> preparation.model(1L, purpose)).getCode());
        }
        verifyNoInteractions(models);
    }
    @Test void disabledConfigurationAndMissingExactModelNeverFallBackToAnotherProvider() {
        ModelConfig config = config("openai"); config.setStatus(0); when(configs.getById(1L)).thenReturn(config);
        assertEquals(400, assertThrows(BusinessException.class, () -> preparation.model(1L, AgentExecutionPreparation.Purpose.INITIAL)).getCode());
        verifyNoInteractions(models);
        config.setStatus(1);
        assertEquals(400, assertThrows(BusinessException.class, () -> preparation.model(1L, AgentExecutionPreparation.Purpose.RESUME)).getCode());
        verify(models, never()).getModel(any()); verify(models, never()).getModelById(any());
    }
    @Test void twoConfigurationsWithTheSameModelNameCannotSubstituteEachOther() {
        var selected = config("openai"); selected.setId(1L); selected.setBaseUrl("https://selected.example/v1");
        var other = config("openai"); other.setId(2L); other.setBaseUrl("https://other.example/v1");
        var mapper = mock(cn.sdh.backend.mapper.ModelConfigMapper.class);
        when(mapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(other);
        cn.sdh.backend.service.factory.strategy.ModelCreationStrategy<ChatModel> strategy = mock(cn.sdh.backend.service.factory.strategy.ModelCreationStrategy.class);
        when(strategy.getProvider()).thenReturn(cn.sdh.backend.service.factory.ModelProvider.OPENAI);
        ChatModel expected = mock(ChatModel.class), wrong = mock(ChatModel.class);
        when(strategy.createModel(any(), any())).thenAnswer(call -> ((ModelConfig) call.getArgument(0)).getId().equals(1L) ? expected : wrong);
        var factory = new ChatModelFactory(mapper, List.of(strategy));
        when(configs.getById(1L)).thenReturn(selected);
        var preparation = new AgentExecutionPreparation(configs, factory, new AgentCapability("openai"), chat);
        for (var purpose : AgentExecutionPreparation.Purpose.values()) {
            assertSame(expected, preparation.model(1L, purpose).model(), "The selected configuration must create the model, even when names collide");
        }
        verifyNoInteractions(mapper);
    }
    @Test void disabledMemoryStillIncludesEvidenceAndToolsWithoutReadingMemory() {
        AgentTool tool = new AgentTool(); tool.setCode("ticket"); tool.setName("创建工单");
        String prompt = preparation.system("原证据", List.of(tool), 7L, "session", "question", false);
        assertTrue(prompt.contains("原证据")); assertTrue(prompt.contains("ticket: 创建工单"));
        verifyNoInteractions(chat);
    }
    @Test void enabledMemoryUsesTheOriginalUserSessionAndQuestion() {
        when(chat.memoryPrompt(anyString(), eq(7L), eq("session"), eq("question"), eq(true))).thenReturn("memory-enriched");
        assertEquals("memory-enriched", preparation.system("证据", List.of(), 7L, "session", "question", true));
    }
    private ModelConfig config(String provider) {
        ModelConfig config = new ModelConfig(); config.setId(1L); config.setProvider(provider); config.setModelId("test-model"); return config;
    }
}
