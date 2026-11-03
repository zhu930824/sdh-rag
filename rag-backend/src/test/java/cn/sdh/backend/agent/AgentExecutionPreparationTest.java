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

    @Test void initialAndResumeUseTheSameModelPolicyAndFallback() {
        ModelConfig config = config("openai");
        when(configs.getById(1L)).thenReturn(config);
        var fallback = mock(ChatModel.class); when(models.getModel(null)).thenReturn(fallback);
        for (var purpose : AgentExecutionPreparation.Purpose.values()) {
            var binding = preparation.model(1L, purpose);
            assertSame(config, binding.config()); assertSame(fallback, binding.model());
        }
    }
    @Test void unsupportedProviderIsRejectedBeforeAnyModelIsCreated() {
        when(configs.getById(1L)).thenReturn(config("deepseek"));
        for (var purpose : AgentExecutionPreparation.Purpose.values()) {
            assertEquals(400, assertThrows(BusinessException.class, () -> preparation.model(1L, purpose)).getCode());
        }
        verifyNoInteractions(models);
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
