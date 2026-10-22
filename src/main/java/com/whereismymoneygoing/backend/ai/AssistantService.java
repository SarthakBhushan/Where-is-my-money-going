package com.whereismymoneygoing.backend.ai;

import com.whereismymoneygoing.backend.nlp.Answer;
import com.whereismymoneygoing.backend.nlp.Language;
import com.whereismymoneygoing.backend.nlp.QueryUnderstandingService;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Flux;

/** Talks to the local Ollama model through Spring AI. Chat history is in memory only, per browser session. */
@Service
public class AssistantService {

    public static final String SUMMARY_PROMPT = "Explain in plain words where my money went in this statement: "
            + "the biggest categories and merchants, how my money in compares with my money out, and anything "
            + "unusual. Then give 3 specific ways I could save money.";

    private static final int MAX_QUESTION_CHARS = 500;
    private static final int HISTORY_MESSAGES = 8;   // last 4 question/answer pairs

    private static final String SYSTEM_PROMPT = """
            You are a friendly personal-finance assistant inside an expense analyser for Indian bank statements.
            Rules:
            - Use ONLY the numbers in FACTS below. Never invent, estimate or recalculate amounts or percentages.
              If something is not in FACTS, say you cannot see it in this statement.
            - Amounts are Indian rupees. Quote them exactly as written in FACTS.
            - Be concise: short paragraphs or at most 5 bullet points, in simple words.
            - Tie every saving tip to a specific category, merchant or recurring payment in FACTS.
              Only mention a saving amount if it already appears in FACTS.
            - "Unrecognised merchants" are payees the app could not identify. Suggest the user check those
              transactions; do not guess what they are.
            - The user may write in English, Hindi (Devanagari) or Hinglish. Understand all three and answer in the
              language you are told to use. Keep merchant names such as Zomato in English letters.
            - You are not a licensed financial adviser. Do not recommend specific stocks, funds or loans.
            """;

    public record Status(boolean ollamaRunning, boolean modelInstalled, String model, String baseUrl) {}

    private final ChatClient chat;
    private final ChatMemory memory = MessageWindowChatMemory.builder().maxMessages(HISTORY_MESSAGES).build();
    private final RestClient probe;
    private final String baseUrl;
    private final String model;
    private final QueryUnderstandingService understanding;

    public AssistantService(ChatClient.Builder builder, QueryUnderstandingService understanding,
                            @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String baseUrl,
                            @Value("${spring.ai.ollama.chat.options.model}") String model) {
        this.chat = builder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .build();
        this.baseUrl = baseUrl;
        this.model = model;
        this.understanding = understanding;

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.probe = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /** Streams the model's reply to the summary button (always English). */
    public Flux<String> stream(String conversationId, String facts, String userMessage) {
        return stream(conversationId, facts, userMessage, Language.ENGLISH, null);
    }

    /**
     * Open-ended question: the model gets all the facts. The facts are re-sent on every call; history only
     * carries the dialogue. {@code previousData} is the data behind the last exact answer, for follow-ups.
     */
    public Flux<String> stream(String conversationId, String facts, String userMessage, Language language,
                               String previousData) {
        StringBuilder system = new StringBuilder(SYSTEM_PROMPT).append('\n').append(language.modelInstruction())
                .append("\n\nFACTS (your only source of numbers):\n").append(facts);
        if (previousData != null) {
            system.append("\nDATA BEHIND YOUR PREVIOUS ANSWER (for follow-up questions):\n").append(previousData);
        }
        return run(conversationId, system.toString(), userMessage);
    }

    /**
     * The exact answer was already calculated in Java and shown to the user. The model only adds a short remark
     * in the user's language, using the labelled data.
     */
    public Flux<String> comment(String conversationId, String facts, String userMessage, Language language,
                                Answer answer) {
        String system = SYSTEM_PROMPT + '\n' + language.modelInstruction()
                + "\n\nFACTS (your only source of numbers):\n" + facts
                + "\nThe user has ALREADY been shown this exact answer, so do not repeat its numbers:\n" + answer.text()
                + "\n\nDATA BEHIND THAT ANSWER:\n" + answer.data()
                + "\nYOUR TASK: add one or two short sentences that help the user understand it (a trend, a comparison "
                + "with another month, or one practical tip). Use only numbers from FACTS or the DATA above. "
                + "Do not start with a greeting.";
        return run(conversationId, system, userMessage);
    }

    private Flux<String> run(String conversationId, String system, String userMessage) {
        String question = FinanceFactsBuilder.clean(userMessage);
        if (question.length() > MAX_QUESTION_CHARS) question = question.substring(0, MAX_QUESTION_CHARS);
        return chat.prompt()
                .system(FinanceFactsBuilder.noBraces(system))   // Spring AI treats { } in prompt text as placeholders
                .user(question)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content();
    }

    public void forget(String conversationId) {
        memory.clear(conversationId);
        understanding.forget(conversationId);
    }

    /** Is Ollama reachable, and is the configured model pulled? Never throws. */
    public Status status() {
        try {
            Map<String, Object> tags = probe.get().uri("/api/tags").retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            boolean installed = false;
            if (tags != null && tags.get("models") instanceof List<?> models) {
                for (Object m : models) {
                    if (m instanceof Map<?, ?> entry && entry.get("name") instanceof String name
                            && (name.equals(model) || name.equals(model + ":latest"))) {
                        installed = true;
                    }
                }
            }
            return new Status(true, installed, model, baseUrl);
        } catch (Exception e) {
            return new Status(false, false, model, baseUrl);
        }
    }

    /** Plain-language explanation for a failure that happened while talking to Ollama. */
    public String describeFailure(Throwable error) {
        StringBuilder all = new StringBuilder();
        for (Throwable t = error; t != null; t = t.getCause()) {
            all.append(t.getClass().getSimpleName()).append(' ').append(t.getMessage()).append(' ');
        }
        String text = all.toString().toLowerCase();
        if (text.contains("connect") || text.contains("refused") || text.contains("unresolved")) {
            return "⚠️ I couldn't reach Ollama at " + baseUrl + ". Start Ollama (see OLLAMA_SETUP.md) and try again.";
        }
        if (text.contains("not found") || text.contains("404")) {
            return "⚠️ The model '" + model + "' isn't installed yet. Run:  ollama pull " + model;
        }
        return "⚠️ The local model returned an error. Check that Ollama is running and has enough free memory.";
    }
}
