package cn.sdh.backend.service.impl;

import cn.sdh.backend.mapper.WebhookConfigMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class WebhookServiceSecurityTest {

    @Test
    void rejectsPlainHttpByDefault() {
        WebhookServiceImpl service = service();

        assertThrows(RuntimeException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "validateNetworkTarget", "http://example.com/hook"));
    }

    @Test
    void rejectsLoopbackEvenWhenPrivateNetworkIsAllowed() {
        WebhookServiceImpl service = service();
        ReflectionTestUtils.setField(service, "allowPrivateNetwork", true);

        assertThrows(RuntimeException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "validateNetworkTarget", "https://127.0.0.1/hook"));
    }

    private WebhookServiceImpl service() {
        WebhookServiceImpl service = new WebhookServiceImpl(mock(WebhookConfigMapper.class), new ObjectMapper());
        ReflectionTestUtils.setField(service, "allowHttp", false);
        ReflectionTestUtils.setField(service, "allowPrivateNetwork", false);
        return service;
    }
}
