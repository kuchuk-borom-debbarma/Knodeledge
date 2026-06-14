package dev.kuku.knodeledge.infra.topo_tracer;

import java.lang.annotation.ElementType;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom application-level annotation for method tracing.
 * Uses KnodeledgeImportanceLevel to provide full type safety and autocomplete.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Traced {
    /**
     * Custom name of the trace span. If empty, falls back to ClassName.MethodName.
     */
    String value() default "";

    /**
     * The custom importance level for the traced method.
     */
    KnodeledgeImportanceLevel type() default KnodeledgeImportanceLevel.METHOD;
}
