package cn.sdh.backend.integration;

import com.alibaba.fastjson2.JSON;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/** Owns an isolated loopback node; never accepts a business cluster URL. */
final class ManagedElasticsearch implements AutoCloseable {
    private final Process process;
    private final int port;
    final Path work;
    ManagedElasticsearch() throws Exception {
        String configured = System.getenv("SDH_ES_IT_HOME");
        if (configured == null) throw new IllegalStateException("Set SDH_ES_IT_HOME to an extracted Elasticsearch 8.18.5 Windows distribution");
        Path home = Path.of(configured).toAbsolutePath();
        work = Files.createTempDirectory("sdh-es-it-");
        Path config = Files.createDirectory(work.resolve("config"));
        for (String name : List.of("jvm.options", "log4j2.properties")) Files.copy(home.resolve("config").resolve(name), config.resolve(name));
        port = freePort();
        String cluster = "sdh-it-" + UUID.randomUUID();
        Files.writeString(config.resolve("elasticsearch.yml"), """
                cluster.name: %s
                network.host: 127.0.0.1
                http.port: %d
                transport.port: %d
                discovery.type: single-node
                xpack.security.enabled: false
                xpack.security.autoconfiguration.enabled: false
                xpack.ml.enabled: false
                ingest.geoip.downloader.enabled: false
                path.data: '%s'
                path.logs: '%s'
                """.formatted(cluster, port, freePort(), work.resolve("data").toString().replace('\\','/'), work.resolve("logs").toString().replace('\\','/')));
        var builder = new ProcessBuilder("cmd.exe", "/d", "/c", home.resolve("bin/elasticsearch.bat").toString());
        builder.directory(home.toFile()).redirectErrorStream(true).redirectOutput(work.resolve("server.log").toFile());
        builder.environment().put("ES_PATH_CONF", config.toString());
        builder.environment().put("ES_JAVA_HOME", home.resolve("jdk").toString());
        builder.environment().put("ES_JAVA_OPTS", "-Xms256m -Xmx256m");
        process = builder.start();
        try {
            var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
            long deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
            while (System.nanoTime() < deadline && process.isAlive()) {
                try {
                    var response = http.send(HttpRequest.newBuilder(URI.create(url())).timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) {
                        var info = JSON.parseObject(response.body());
                        if (!cluster.equals(info.getString("cluster_name"))) throw new IllegalStateException("Refusing an unowned Elasticsearch node");
                        if (!"8.18.5".equals(info.getJSONObject("version").getString("number"))) throw new IllegalStateException("Expected Elasticsearch 8.18.5");
                        return;
                    }
                } catch (java.io.IOException unavailable) { /* The owned node is starting. */ }
                Thread.sleep(250);
            }
            throw new IllegalStateException("Elasticsearch failed to start; see " + work.resolve("server.log"));
        } catch (Exception failure) { close(); throw failure; }
    }
    String url() { return "http://127.0.0.1:" + port; }
    private static int freePort() throws Exception { try (var socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) { return socket.getLocalPort(); } }
    @Override public void close() {
        var descendants = process.descendants().toList();
        descendants.reversed().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        System.out.println("Owned Elasticsearch evidence retained at " + work);
    }
}
