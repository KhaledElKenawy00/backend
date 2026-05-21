package com.virtualoffice.chat_service.config;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;

@Slf4j
@Aspect
@Component
public class LoggingAspect {

    @Pointcut("execution(* com.virtualoffice.chat_service.service.impl..*(..))")
    private void serviceMethods() {}

    // skip HealthController — health probes hit continuously and would drown out the rest
    @Pointcut("execution(* com.virtualoffice.chat_service.controller..*(..)) "
            + "&& !execution(* com.virtualoffice.chat_service.controller.HealthController.*(..))")
    private void controllerMethods() {}

    @Around("serviceMethods()")
    public Object logServiceCall(ProceedingJoinPoint jp) throws Throwable {
        String method = jp.getSignature().toShortString();
        log.debug(">>> {} args={}", method, summarizeArgs(jp.getArgs()));
        long start = System.currentTimeMillis();
        try {
            Object result = jp.proceed();
            log.debug("<<< {} returned in {}ms", method, System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            // 4xx is normal client behaviour, not a service fault — keep it quiet
            if (e instanceof ResponseStatusException) {
                log.debug("--- {} rejected with {} after {}ms: {}",
                        method, e.getClass().getSimpleName(), elapsed, e.getMessage());
            } else {
                log.warn("!!! {} threw {} after {}ms: {}",
                        method, e.getClass().getSimpleName(), elapsed, e.getMessage());
            }
            throw e;
        }
    }

    @Around("controllerMethods()")
    public Object logControllerCall(ProceedingJoinPoint jp) throws Throwable {
        String method = jp.getSignature().toShortString();
        log.info("=> {}", method);
        long start = System.currentTimeMillis();
        try {
            Object result = jp.proceed();
            log.info("<= {} completed in {}ms", method, System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            if (e instanceof ResponseStatusException) {
                log.info("<= {} rejected after {}ms: {}", method, elapsed, e.getMessage());
            } else {
                log.error("<= {} failed after {}ms: {}", method, elapsed, e.getMessage());
            }
            throw e;
        }
    }

    // class names only — argument values may contain user content we don't want in logs
    private String summarizeArgs(Object[] args) {
        if (args == null || args.length == 0) return "[]";
        return Arrays.stream(args)
                .map(a -> a == null ? "null" : a.getClass().getSimpleName())
                .toList()
                .toString();
    }
}
