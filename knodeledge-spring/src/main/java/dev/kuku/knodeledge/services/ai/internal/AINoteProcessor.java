package dev.kuku.knodeledge.services.ai.internal;

import dev.kuku.topotracer.sdk.Span;
import dev.kuku.topotracer.sdk.TraceContext;
import dev.kuku.topotracer.sdk.Tracer;
import dev.kuku.topotracer.sdk.TopoNodeType;
import dev.kuku.topotracer.sdk.TopoImportance;
import dev.kuku.topotracer.sdk.TraceOptions;
import dev.kuku.topotracer.spring.Traced;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class AINoteProcessor {

    @Autowired
    private Tracer tracer;

    @Traced(value = "ai.process-single-note", type = TopoNodeType.METHOD, dynamicImportance = true)
    public void processSingleNote(String note) {
        Span active = TraceContext.getActive();
        if (active != null) {
            active.setAttribute("note.content", note);
        }

        // Test programmatic tracing inside AOP span using NodeType and Importance Enums
        try {
            tracer.trace("ai.llm-call", () -> {
                Span llmSpan = TraceContext.getActive();
                if (llmSpan != null) {
                    llmSpan.setAttribute("note.length", note.length());
                }
                tracer.log("Simulating LLM request processing", Map.of("length", String.valueOf(note.length())));
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }, TraceOptions.builder().nodeType(TopoNodeType.METHOD).importance(TopoImportance.DYNAMIC));
        } catch (Exception e) {
            // Ignore mock exceptions
        }

        // Test custom mapping resolution
        try {
            tracer.trace("ai.custom-step", () -> {
                System.out.println("Running custom-step...");
                tracer.log("Running custom operation step");
                return null;
            }, TraceOptions.builder().nodeType("custom-op"));
        } catch (Exception e) {
            // Ignore mock exceptions
        }
    }
}
