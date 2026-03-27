package cn.sdh.backend.aop;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.entity.OperationLog;
import cn.sdh.backend.service.OperationLogService;
import com.alibaba.fastjson2.JSON;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogService operationLogService;

    @Pointcut("execution(* cn.sdh.backend.controller..*.*(..))")
    public void controllerPointcut() {}

    @AfterReturning(pointcut = "controllerPointcut()", returning = "result")
    public void doAfterReturning(JoinPoint joinPoint, Object result) {
        saveLog(joinPoint, null, result);
    }

    @AfterThrowing(pointcut = "controllerPointcut()", throwing = "e")
    public void doAfterThrowing(JoinPoint joinPoint, Exception e) {
        saveLog(joinPoint, e, null);
    }

    private void saveLog(JoinPoint joinPoint, Exception e, Object result) {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) return;

            HttpServletRequest request = attributes.getRequest();
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();

            OperationLog operationLog = new OperationLog();
            operationLog.setCreateTime(LocalDateTime.now());

            Long userId = UserContext.getUserId();
            String username = UserContext.getUsername();
            operationLog.setUserId(userId);
            operationLog.setUsername(username != null ? username : "anonymous");

            operationLog.setRequestUrl(request.getRequestURI());
            operationLog.setRequestMethod(request.getMethod());
            operationLog.setIp(getIpAddress(request));

            String methodName = method.getName();
            operationLog.setType(getOperationType(methodName));
            operationLog.setContent(getOperationContent(methodName, joinPoint.getArgs()));

            if (e != null) {
                operationLog.setStatus(0);
                operationLog.setErrorMsg(e.getMessage());
            } else {
                operationLog.setStatus(1);
                if (result != null) {
                    String resultJson = JSON.toJSONString(result);
                    if (resultJson.length() > 2000) {
                        resultJson = resultJson.substring(0, 2000) + "...";
                    }
                    operationLog.setResponseData(resultJson);
                }
            }

            String browser = request.getHeader("User-Agent");
            if (browser != null && browser.length() > 100) {
                browser = browser.substring(0, 100);
            }
            operationLog.setBrowser(browser);

            String params = getRequestParams(joinPoint);
            if (params.length() > 2000) {
                params = params.substring(0, 2000) + "...";
            }
            operationLog.setRequestParams(params);

            operationLogService.save(operationLog);
        } catch (Exception ex) {
            log.error("保存操作日志失败", ex);
        }
    }

    private String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    private String getOperationType(String methodName) {
        if (methodName.startsWith("login")) return "login";
        if (methodName.startsWith("logout")) return "logout";
        if (methodName.startsWith("save") || methodName.startsWith("create") || methodName.startsWith("add")) return "create";
        if (methodName.startsWith("update") || methodName.startsWith("edit") || methodName.startsWith("modify")) return "update";
        if (methodName.startsWith("delete") || methodName.startsWith("remove")) return "delete";
        if (methodName.startsWith("export")) return "export";
        if (methodName.startsWith("import") || methodName.startsWith("upload")) return "import";
        return "query";
    }

    private String getOperationContent(String methodName, Object[] args) {
        String className = args.length > 0 ? args[0].getClass().getSimpleName() : "";
        return methodName + " " + className;
    }

    private String getRequestParams(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) return "";

        Map<String, Object> params = new HashMap<>();
        String[] paramNames = ((MethodSignature) joinPoint.getSignature()).getParameterNames();

        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof MultipartFile) continue;
            if (args[i] instanceof HttpServletRequest) continue;
            if (args[i] instanceof HttpServletResponse) continue;
            if (paramNames != null && i < paramNames.length) {
                params.put(paramNames[i], args[i]);
            }
        }

        return JSON.toJSONString(params);
    }
}