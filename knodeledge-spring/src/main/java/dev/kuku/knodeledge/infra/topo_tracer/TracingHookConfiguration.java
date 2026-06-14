package dev.kuku.knodeledge.infra.topo_tracer;

import dev.kuku.topotracer.sdk.LogHook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class TracingHookConfiguration {

    @Bean
    public LogHook slf4jLogHook() {
        return (message, data, _) -> log.info("[Trace Log] {}, {}", message, data);
    }

    @Bean
    public dev.kuku.topotracer.spring.TracerBuilderCustomizer topoTracerCustomizer() {
        return builder -> {
            builder.ignoreFailures(true);
            builder.importance(
                KnodeledgeImportance.CONTROLLER,
                KnodeledgeImportance.SERVICE,
                KnodeledgeImportance.REPOSITORY,
                KnodeledgeImportance.DATABASE,
                KnodeledgeImportance.EXTERNAL_API,
                KnodeledgeImportance.REMOTE_CALL,
                KnodeledgeImportance.IO,
                KnodeledgeImportance.METHOD,
                KnodeledgeImportance.DYNAMIC
            );
            builder.nodeTypeImportance("controller", 0);
            builder.nodeTypeImportance("service", 0);
            builder.nodeTypeImportance("db-call", 1);
            builder.nodeTypeImportance("remote-call", 1);
            builder.nodeTypeImportance("io", 2);
            builder.nodeTypeImportance("method", 3);
        };
    }

    @Bean
    public org.springframework.ai.chat.client.ChatClient chatClient(org.springframework.ai.chat.client.ChatClient.Builder builder) {
        return builder.build();
    }
}



