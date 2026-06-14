package dev.kuku.knodeledge.infra;

import dev.kuku.topotracer.sdk.LogHook;
import dev.kuku.topotracer.sdk.TraceHook;
import dev.kuku.topotracer.sdk.Span;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class TracingHookConfiguration {

    @Bean
    public LogHook slf4jLogHook() {
        return (message, data, importanceLevel) -> {
            log.info("[Trace Log] {} | data: {} | importance: {}", message, data, importanceLevel);
        };
    }

    @Bean
    public TraceHook slf4jTraceHook() {
        return new TraceHook() {
            @Override
            public void onSpanStart(Span span) {
                log.info("[Span Start] {} | id: {} | traceId: {} | type: {} | importance: {}",
                    span.getStartMessage(), span.getId(), span.getTraceId(), span.getNodeType(), span.getImportanceLevel());
            }

            @Override
            public void onSpanEnd(Span span) {
                log.info("[Span End] {} | id: {} | traceId: {} | duration: {}ms",
                    span.getStartMessage(), span.getId(), span.getTraceId(),
                    (System.currentTimeMillis() - span.getStartedAt()));
            }
        };
    }
}
