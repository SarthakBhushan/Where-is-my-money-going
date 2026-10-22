package com.whereismymoneygoing.backend.web;

import com.whereismymoneygoing.backend.ai.AssistantService;
import com.whereismymoneygoing.backend.ai.FinanceFactsBuilder;
import com.whereismymoneygoing.backend.model.ParsedStatement;
import com.whereismymoneygoing.backend.nlp.QueryUnderstandingService;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    public record ChatRequest(String message) {}

    private static final MediaType TEXT_UTF8 = new MediaType("text", "plain", StandardCharsets.UTF_8);

    private final AssistantService assistant;
    private final FinanceFactsBuilder facts;
    private final QueryUnderstandingService understanding;

    public AssistantController(AssistantService assistant, FinanceFactsBuilder facts,
                               QueryUnderstandingService understanding) {
        this.assistant = assistant;
        this.facts = facts;
        this.understanding = understanding;
    }

    @GetMapping("/status")
    public AssistantService.Status status() {
        return assistant.status();
    }

    @PostMapping("/summary")
    public ResponseEntity<StreamingResponseBody> summary(HttpSession session) {
        ParsedStatement statement = current(session);
        if (statement == null) return message(HttpStatus.CONFLICT, "Upload a statement first.");
        return stream(assistant.stream(session.getId(), facts.build(statement), AssistantService.SUMMARY_PROMPT));
    }

    @PostMapping("/chat")
    public ResponseEntity<StreamingResponseBody> chat(@RequestBody ChatRequest request, HttpSession session) {
        ParsedStatement statement = current(session);
        if (statement == null) return message(HttpStatus.CONFLICT, "Upload a statement first.");
        if (request == null || request.message() == null || request.message().isBlank()) {
            return message(HttpStatus.BAD_REQUEST, "Type a question first.");
        }
        String chatId = session.getId();
        String allFacts = facts.build(statement);
        var understood = understanding.understand(chatId, request.message(), statement);
        if (!understood.answered()) {
            // open-ended or advice question: the model answers from the full facts, in the user's language
            return stream(assistant.stream(chatId, allFacts, request.message(), understood.language(),
                    understanding.lastData(chatId)));
        }
        // exact answer first (calculated in Java, shown even if Ollama is off), then a short remark from the model
        Flux<String> exact = Flux.just(understood.answer().text() + "\n\n");
        Flux<String> remark = assistant.comment(chatId, allFacts, request.message(), understood.language(),
                understood.answer()).onErrorResume(e -> Flux.just(assistant.describeFailure(e)));
        return stream(Flux.concat(exact, remark));
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> reset(HttpSession session) {
        assistant.forget(session.getId());
        return ResponseEntity.noContent().build();
    }

    // ---------- helpers ----------

    /** Writes tokens to the browser as they arrive; failures become a readable message instead of a broken page. */
    private ResponseEntity<StreamingResponseBody> stream(Flux<String> tokens) {
        StreamingResponseBody body = out -> {
            Iterator<String> it = tokens.toIterable().iterator();
            try {
                while (it.hasNext()) {
                    out.write(it.next().getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
            } catch (IOException clientLeft) {
                // browser closed the tab; fall through to cancel the model call
            } catch (RuntimeException e) {
                out.write(assistant.describeFailure(e).getBytes(StandardCharsets.UTF_8));
            } finally {
                if (it instanceof Disposable d) d.dispose();
            }
        };
        return ResponseEntity.ok().contentType(TEXT_UTF8).header("X-Accel-Buffering", "no").body(body);
    }

    private static ResponseEntity<StreamingResponseBody> message(HttpStatus status, String text) {
        return ResponseEntity.status(status).contentType(TEXT_UTF8)
                .body(out -> out.write(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static ParsedStatement current(HttpSession session) {
        return (ParsedStatement) session.getAttribute(StatementController.SESSION_KEY);
    }
}
