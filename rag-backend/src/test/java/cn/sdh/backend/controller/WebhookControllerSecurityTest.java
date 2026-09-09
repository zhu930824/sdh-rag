package cn.sdh.backend.controller;

import cn.sdh.backend.entity.WebhookConfig;
import cn.sdh.backend.service.PlatformAdminService;
import cn.sdh.backend.service.WebhookService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookControllerSecurityTest {

    @Test
    void requiresAdminAndRemovesStoredCredentialsFromResponse() {
        WebhookService webhooks = mock(WebhookService.class);
        PlatformAdminService admins = mock(PlatformAdminService.class);
        WebhookConfig stored = new WebhookConfig();
        stored.setId(12L);
        stored.setSecret("signing-secret");
        stored.setHeaders("{\"Authorization\":\"Bearer secret\"}");
        when(webhooks.getById(12L)).thenReturn(stored);

        WebhookConfig response = new WebhookController(webhooks, admins).get(12L).getData();

        verify(admins).requireAdmin();
        assertNull(response.getSecret());
        assertNull(response.getHeaders());
    }
}
