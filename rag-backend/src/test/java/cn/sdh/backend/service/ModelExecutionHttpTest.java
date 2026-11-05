package cn.sdh.backend.service;

import cn.sdh.backend.entity.ModelConfig;
import cn.sdh.backend.mapper.ModelConfigMapper;
import cn.sdh.backend.service.factory.EmbeddingModelFactory;
import cn.sdh.backend.service.impl.EmbeddingServiceImpl;
import com.sun.net.httpserver.HttpServer;
import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ModelExecutionHttpTest {
    @Test void nativeEmbeddingClientKeepsTheFrozenEndpointModelAndCredentialsAcrossCalls() throws Exception {
        var requests=new CopyOnWriteArrayList<String>();
        var authorizations=new CopyOnWriteArrayList<String>();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/embeddings",exchange -> {
            requests.add(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body="{\"object\":\"list\",\"data\":[{\"object\":\"embedding\",\"index\":0,\"embedding\":[1.0,0.0]}],\"model\":\"frozen-model\",\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        var mapper=mock(ModelConfigMapper.class);
        var config=new ModelConfig(); config.setId(1L); config.setName("alias"); config.setModelType("embedding");
        config.setModelId("frozen-model"); config.setProvider("openai"); config.setStatus(1);
        config.setBaseUrl("http://127.0.0.1:"+server.getAddress().getPort()); config.setApiKey("frozen-private-key");
        var factory=new EmbeddingModelFactory(mapper);
        var embeddings=new EmbeddingServiceImpl(factory,null);
        try(var scope=new ModelExecutionScope(List.of(config))) {
            config.setModelId("changed-model"); config.setApiKey("changed-private-key"); config.setBaseUrl("http://127.0.0.1:1");
            assertArrayEquals(new float[]{1,0},embeddings.getEmbeddingByModel("first","alias"));
            factory.clearCache();
            assertArrayEquals(new float[]{1,0},embeddings.getEmbeddingByModel("second","alias"));
            scope.check();
            assertEquals(2,requests.size());
            requests.forEach(r -> assertEquals("frozen-model",JSON.parseObject(r).getString("model")));
            assertEquals(List.of("Bearer frozen-private-key","Bearer frozen-private-key"),authorizations);
            String manifest=JSON.toJSONString(scope.bindings());
            assertFalse(manifest.contains("private-key")); assertFalse(manifest.contains("127.0.0.1"));
            assertEquals(1,scope.bindings().size()); verifyNoInteractions(mapper);
        } finally { server.stop(0); }
        assertFalse(ModelExecutionScope.active());
    }
}
