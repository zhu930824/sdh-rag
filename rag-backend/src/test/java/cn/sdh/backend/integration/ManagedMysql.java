package cn.sdh.backend.integration;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.*;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Owns exactly one child mysqld and its fresh data directory. Never stops a system service. */
final class ManagedMysql implements AutoCloseable {
    private final Path binary;
    private final Path root;
    private final Path data;
    private final int port;
    private Process process;
    private String password = "";
    ManagedMysql() throws Exception {
        String configured = System.getenv("SDH_MYSQL_IT_MYSQLD");
        if (configured == null || configured.isBlank()) throw new IllegalStateException("mysql-it requires SDH_MYSQL_IT_MYSQLD pointing to an isolated MySQL 8.x mysqld binary; no tests were silently skipped");
        binary = Path.of(configured).toAbsolutePath().normalize();
        if (!Files.isRegularFile(binary)) throw new IllegalStateException("MySQL test executable does not exist");
        root = Files.createTempDirectory("sdh-agent-mysql-it-").toAbsolutePath();
        data = root.resolve("data");
        try (var socket = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) { port = socket.getLocalPort(); }
        Process init = new ProcessBuilder(binary.toString(), "--no-defaults", "--initialize-insecure", "--datadir=" + data,
                "--log-error=" + root.resolve("initialize.log")).redirectErrorStream(true).redirectOutput(root.resolve("initialize-console.log").toFile()).start();
        if (!init.waitFor(90, TimeUnit.SECONDS)) { init.destroyForcibly(); throw new IllegalStateException("MySQL initialization timed out; logs: " + root); }
        if (init.exitValue() != 0) throw new IllegalStateException("MySQL initialization failed; logs: " + root);
        try {
            start();
            String generated = UUID.randomUUID().toString().replace("-", "");
            admin().execute("ALTER USER 'root'@'localhost' IDENTIFIED BY '" + generated + "'");
            password = generated;
        } catch (Exception failure) { close(); throw failure; }
        System.out.println("Managed MySQL " + admin().queryForObject("SELECT VERSION()", String.class) + "; evidence directory: " + root);
    }
    String url(String schema) { return "jdbc:mysql://127.0.0.1:" + port + "/" + schema + "?useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=2000&socketTimeout=5000&serverTimezone=UTC"; }
    String password() { return password; }
    Path evidence() { return root; }
    DriverManagerDataSource source(String schema) { return new DriverManagerDataSource(url(schema), "root", password); }
    JdbcTemplate admin() { return new JdbcTemplate(source("")); }
    void start() throws Exception {
        if (process != null && process.isAlive()) throw new IllegalStateException("Managed MySQL is already running");
        process = new ProcessBuilder(binary.toString(), "--no-defaults", "--datadir=" + data,
                "--bind-address=127.0.0.1", "--port=" + port, "--mysqlx=OFF",
                "--innodb-buffer-pool-size=64M", "--log-error=" + root.resolve("server.log"))
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(root.resolve("server-console.log").toFile())).start();
        long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
        while (System.nanoTime() < deadline && process.isAlive()) {
            try { admin().queryForObject("SELECT 1", Integer.class); return; }
            catch (RuntimeException notReady) { Thread.sleep(200); }
        }
        throw new IllegalStateException("Managed MySQL did not start; logs: " + root);
    }
    void crash() throws Exception {
        if (process == null || !process.isAlive()) throw new IllegalStateException("Managed MySQL is not running");
        var descendants = process.descendants().toList();
        process.destroyForcibly();
        if (!process.waitFor(15, TimeUnit.SECONDS)) throw new IllegalStateException("Managed MySQL did not stop");
        for (var child : descendants) {
            child.destroyForcibly();
            child.onExit().get(10, TimeUnit.SECONDS);
        }
    }
    @Override public void close() {
        if (process == null || !process.isAlive()) return;
        try { admin().execute("SHUTDOWN"); } catch (RuntimeException ignored) { /* Connection normally closes during SHUTDOWN. */ }
        try { if (!process.waitFor(15, TimeUnit.SECONDS)) { process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly().waitFor(5, TimeUnit.SECONDS); } }
        catch (InterruptedException interrupted) { process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); Thread.currentThread().interrupt(); }
    }
}
