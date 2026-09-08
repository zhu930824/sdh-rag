package cn.sdh.backend.service;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.entity.User;
import cn.sdh.backend.entity.AgentTool;
import cn.sdh.backend.mapper.AgentToolMapper;
import cn.sdh.backend.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentToolServiceTest {
    @Mock AgentToolMapper tools;
    @Mock UserMapper users;
    @Mock AgentToolHttpClient httpClient;
    AgentToolService service;

    @BeforeEach void setup() {
        UserContext context = new UserContext();
        context.setUserId(7L);
        UserContext.setUserContext(context);
        service = new AgentToolService(tools, users, httpClient);
    }

    @AfterEach void cleanup() { UserContext.clear(); }

    @Test void ordinaryUserCannotRegisterInternalDispatcherEndpoint() {
        BusinessException error = assertThrows(BusinessException.class, () ->
                service.create(command("internal://demo/createServiceTicket", null)));
        assertEquals(403, error.getCode());
        verifyNoInteractions(users, httpClient, tools);
    }

    @Test void validatesInputSchemaBeforePersisting() {
        User user = new User();
        user.setId(7L);
        user.setStatus(1);
        when(users.selectById(7L)).thenReturn(user);

        BusinessException error = assertThrows(BusinessException.class, () ->
                service.create(command("https://business.example.com/tickets", "not-json")));

        assertEquals(400, error.getCode());
        verify(tools, never()).insert(any(AgentTool.class));
    }

    private AgentToolService.ToolCommand command(String endpoint, String schema) {
        return new AgentToolService.ToolCommand("创建工单", "create_ticket", "测试", endpoint, "POST",
                null, "none", null, "HIGH", "ALWAYS", null, 30,
                false, false, 1, schema);
    }
}
