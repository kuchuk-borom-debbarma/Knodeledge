package dev.kuku.knodeledge.infra.topo_tracer;

import dev.kuku.topotracer.sdk.Span;
import dev.kuku.topotracer.sdk.TraceContext;
import dev.kuku.topotracer.sdk.TraceOptions;
import dev.kuku.topotracer.sdk.Tracer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;


/**
 * Aspect that intercepts methods annotated with the custom {@link Traced} annotation.
 */
@Aspect
@Component
public class KnodeledgeTracingAspect {
    private final Tracer tracer;

    public KnodeledgeTracingAspect(Tracer tracer) {
        this.tracer = tracer;
    }

    @SuppressWarnings("unused")
    @Around("@annotation(traced)")
    public Object traceMethod(ProceedingJoinPoint joinPoint, Traced traced) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = method.getName();

        String spanName = traced.value();
        if (spanName.isEmpty()) {
            spanName = className + "." + methodName;
        }

        String paramTypes = Arrays.stream(method.getParameterTypes())
            .map(Class::getSimpleName)
            .collect(Collectors.joining(", "));
        String autoName = className + "." + methodName + "(" + paramTypes + ")";

        // Map the custom enum level to KnodeledgeImportance
        KnodeledgeImportanceLevel typeEnum = traced.type();
        KnodeledgeImportance importance = getKnodeledgeImportance(typeEnum);

        TraceOptions options = TraceOptions.builder()
            .nodeType(typeEnum != null ? typeEnum.name().toLowerCase() : "method")
            .name(autoName)
            .importance(importance);

        Span span = tracer.startNode(spanName, options);
        Span parent = TraceContext.getActive();
        TraceContext.setActive(span);

        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            span.setAttribute("error", true);
            span.setAttribute("error.message", t.getMessage());
            throw t;
        } finally {
            span.end();
            TraceContext.setActive(parent);
        }
    }

    private static @NonNull KnodeledgeImportance getKnodeledgeImportance(KnodeledgeImportanceLevel typeEnum) {
        KnodeledgeImportance importance = KnodeledgeImportance.DYNAMIC;
        if (typeEnum != null) {
            switch (typeEnum) {
                case CONTROLLER -> importance = KnodeledgeImportance.CONTROLLER;
                case SERVICE -> importance = KnodeledgeImportance.SERVICE;
                case REPOSITORY -> importance = KnodeledgeImportance.REPOSITORY;
                case DATABASE -> importance = KnodeledgeImportance.DATABASE;
                case EXTERNAL_API -> importance = KnodeledgeImportance.EXTERNAL_API;
                case REMOTE_CALL -> importance = KnodeledgeImportance.REMOTE_CALL;
                case IO -> importance = KnodeledgeImportance.IO;
                case METHOD -> importance = KnodeledgeImportance.METHOD;
            }
        }
        return importance;
    }
}
