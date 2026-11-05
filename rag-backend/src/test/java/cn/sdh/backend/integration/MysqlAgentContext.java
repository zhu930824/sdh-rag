package cn.sdh.backend.integration;

import cn.sdh.backend.agent.*;
import cn.sdh.backend.agent.runtime.JdbcAgentStateStore;
import cn.sdh.backend.demo.InternalToolDispatcher;
import cn.sdh.backend.mapper.*;
import cn.sdh.backend.service.*;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

final class MysqlAgentContext {
    static AnnotationConfigApplicationContext open(DataSource source) {
        var context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> source);
        context.register(Config.class); context.refresh(); return context;
    }
    @Configuration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = AgentRunMapper.class)
    @Import({RunExecutionLeaseService.class, RunStateService.class, AgentRunLifecycle.class, AgentExecutionAttemptService.class, JdbcAgentStateStore.class,
            AgentRunRecoveryService.class, AgentActionService.class, AgentActionJobs.class,
            AgentActionExecutor.class, AgentActionCompletionService.class, PlatformJobService.class, PublishedIndexService.class, IndexPublicationGateStore.class,IndexGenerationLifecycle.class,cn.sdh.backend.config.IndexLifecycleProperties.class,
            EvaluationReplayService.class,EvaluationExecutionService.class,KnowledgePublishingService.class,cn.sdh.backend.agent.evaluation.AgentEvaluationService.class})
    static class Config {
        @Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }
        @Bean PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(source); return factory.getObject();
        }
        // Authorization/provider services are outside this persistence/fault acceptance suite.
        @Bean AgentToolService tools(AgentToolMapper tools) {
            var service = mock(AgentToolService.class);
            when(service.requireUsable(anyLong(), anyLong())).thenAnswer(call -> tools.selectById((Long) call.getArgument(0)));
            return service;
        }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean AgentToolHttpClient http(ObjectMapper json) { return new AgentToolHttpClient(json, mock(KnowledgeSourceCredentialResolver.class)); }
        @Bean InternalToolDispatcher internal() { return mock(InternalToolDispatcher.class); }
        @Bean AgentImprovementService improvements() { return mock(AgentImprovementService.class); }
        @Bean KnowledgeAccessService knowledgeAccess(KnowledgeBaseMapper bases) {
            var service=mock(KnowledgeAccessService.class);
            when(service.requireRead(anyLong(),anyLong())).thenAnswer(call -> bases.selectById((Long)call.getArgument(0)));
            when(service.requireEdit(anyLong(),anyLong())).thenAnswer(call -> bases.selectById((Long)call.getArgument(0)));
            return service;
        }
        @Bean DocumentProcessService documentProcessor() { return mock(DocumentProcessService.class); }
        @Bean VectorStoreService vectors() { return mock(VectorStoreService.class); }
        @Bean MinioService minio() {
            var service=mock(MinioService.class);
            when(service.uploadFile(any(),anyString(),anyString(),anyLong())).thenAnswer(call -> call.getArgument(1));
            return service;
        }
    }
}
