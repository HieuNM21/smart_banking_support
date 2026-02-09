package com.example.smart_banking_support.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.util.Arrays;

//@Aspect
//@Component
@Slf4j
public class LoggingAspect {

    /**
     * Định nghĩa vùng cần log: Tất cả các file trong package service và controller
     */
    @Pointcut("within(com.example.smart_banking_support.service..*) || within(com.example.smart_banking_support.controller..*)")
    public void applicationPackagePointcut() {
    }

    /**
     * Log khi phương thức chạy xong (thành công hoặc thất bại)
     * Đo thời gian thực thi của phương thức
     */
    @Around("applicationPackagePointcut()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            String className = joinPoint.getSignature().getDeclaringTypeName();
            String methodName = joinPoint.getSignature().getName();

            log.info("➡️ ENTER: {}.{}() với tham số = {}", className, methodName, Arrays.toString(joinPoint.getArgs()));

            Object result = joinPoint.proceed(); // Cho phép hàm chạy

            long elapsedTime = System.currentTimeMillis() - start;
            log.info("⬅️ EXIT: {}.{}() trong {}ms", className, methodName, elapsedTime);

            return result;
        } catch (IllegalArgumentException e) {
            log.error("❌ Illegal argument: {} in {}.{}()", Arrays.toString(joinPoint.getArgs()),
                    joinPoint.getSignature().getDeclaringTypeName(), joinPoint.getSignature().getName());
            throw e;
        }
    }

    /**
     * Log khi có Exception ném ra
     */
    @AfterThrowing(pointcut = "applicationPackagePointcut()", throwing = "e")
    public void logAfterThrowing(JoinPoint joinPoint, Throwable e) {
        log.error("🔥 EXCEPTION tại {}.{}() với nguyên nhân: {}",
                joinPoint.getSignature().getDeclaringTypeName(),
                joinPoint.getSignature().getName(),
                e.getMessage() != null ? e.getMessage() : "NULL");
    }
}