package com.cloudkeeper.cloudbalance_backend.logging.aspect;

import com.cloudkeeper.cloudbalance_backend.logging.Logger;
import com.cloudkeeper.cloudbalance_backend.logging.LoggerFactory;
import com.cloudkeeper.cloudbalance_backend.logging.annotation.Loggable;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.Arrays;


@Component
@Aspect
public class LoggingAspect {
    @Around("@annotation(loggable)")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint, Loggable loggable) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Logger logger = LoggerFactory.getLogger(signature.getDeclaringType());

        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();

        if (loggable.logArgs()) {
            logger.info(">>> Starting {}.{}() with args : {}", className, methodName, Arrays.toString(joinPoint.getArgs()));
        } else {
            logger.info(">>> Starting {}.{}()", className, methodName);
        }

        long startTime = System.currentTimeMillis();
        Object result = null;

        try {
            result = joinPoint.proceed();
            return result;
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;

            // log execution time
            if (loggable.logExecutionTime()) {
                logger.info("<<< Completed {}.{}() in {} ms", className, methodName, executionTime);
            }
            // log result
            if (loggable.logResult() && result != null) {
                logger.info("Result : {}", result);
            }
        }
    }
}
