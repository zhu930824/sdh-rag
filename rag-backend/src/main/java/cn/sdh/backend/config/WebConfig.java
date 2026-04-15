package cn.sdh.backend.config;

import cn.sdh.backend.interceptor.JwtInterceptor;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * WebMvc配置
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private JwtInterceptor jwtInterceptor;

    @Value("${file.upload.path:./uploads}")
    private String uploadPath;

    private String uploadAbsolutePath;

    @PostConstruct
    public void init() {
        // 转换为绝对路径
        Path path = Paths.get(uploadPath).toAbsolutePath().normalize();
        this.uploadAbsolutePath = path.toString();
    }

    /**
     * 注册JWT拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**") // 拦截所有路径
                .excludePathPatterns(
                        // 登录注册相关
                        "/api/user/login",
                        "/api/user/register",
                        // 静态资源
                        "/static/**",
                        "/upload/**",
                        "/uploads/**",
                        // Swagger相关
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/swagger-resources/**",
                        "/webjars/**",
                        // 错误页面
                        "/error",
                        // 健康检查
                        "/actuator/**"
                );
    }

    /**
     * 配置静态资源映射
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 配置上传文件访问路径 - 使用绝对路径
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadAbsolutePath + "/");

        // 配置静态资源访问路径
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
    }
}
