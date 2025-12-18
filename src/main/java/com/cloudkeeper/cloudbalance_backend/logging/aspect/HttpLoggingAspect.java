package com.cloudkeeper.cloudbalance_backend.logging.aspect;

import com.cloudkeeper.cloudbalance_backend.logging.Logger;
import com.cloudkeeper.cloudbalance_backend.logging.LoggerFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Aspect
@Component
public class HttpLoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(HttpLoggingAspect.class);

    @Pointcut("execution (* com.cloudkeeper.cloudbalance_backend.controller..*(..))")
    public void controllerMethods() {
    }

    @Around("controllerMethods()")
    public Object logHttpRequest(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();

            // log request details
            logger.info("============= HTTP REQUEST ===============");
            logger.info("URL: {} {}", request.getMethod(), request.getRequestURI());
            logger.info("Client IP: {}", getClientIp(request));
            logger.info("User-Agent: {}", request.getHeader("User-Agent"));

            // log headers (excluding sensitive ones)
            Map<String, String> headers = getHeaders(request);
            logger.debug("Headers : {}", headers);

            long startTime = System.currentTimeMillis();
            Object result = null;

            try {
                result = joinPoint.proceed();
                long executionTime = System.currentTimeMillis() - startTime;
                logger.info("Response Time : {} ms", executionTime);
                logger.info("==================== Request Completed ===================");
                return result;
            } catch (Exception e) {
                long executionTime = System.currentTimeMillis() - startTime;
                logger.error("Request failed after {} ms : {}", e, executionTime, e.getMessage());
                throw e;
            }
        }
        return joinPoint.proceed();
    }

    private Map<String, String> getHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();

        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            // mask sensitive headers
            if (headerName.equalsIgnoreCase("Authorization")) {
                headers.put(headerName, "Bearer ***");
            } else if (headerName.equalsIgnoreCase("Cookie")) {
                headers.put(headerName, "***");
            } else {
                headers.put(headerName, request.getHeader(headerName));
            }
        }
        return headers;
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

}
