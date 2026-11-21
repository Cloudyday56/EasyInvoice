package com.brim.easyinvoice.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Minimal OpenRouter client (OpenAI-compatible chat completions API).
 * The key comes only from the OPENROUTER_API_KEY environment variable (see application.yaml).
 * Switch models with LLM_MODEL, e.g. "anthropic/claude-sonnet-4.5" or "openai/gpt-4o-mini".
 */
@Component
public class LlmClient {

    private final RestClient http;
    private final String apiKey;
    private final String model;

    public LlmClient(RestClient.Builder builder,
                     @Value("${app.llm.base-url}") String baseUrl,
                     @Value("${app.llm.api-key}") String apiKey,
                     @Value("${app.llm.model}") String model) {
        this.http = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.model = model;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String model() {
        return model;
    }

    /** Simple text in, text out. */
    public String complete(String systemPrompt, String userPrompt) {
        return chat(List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)));
    }

    /**
     * Sends chat messages and returns the first reply's text.
     * A message's "content" can be a string, or a list of parts for images/PDFs:
     *   {"type": "text", "text": "..."}
     *   {"type": "image_url", "image_url": {"url": "data:image/png;base64,..."}}
     *   {"type": "file", "file": {"filename": "x.pdf", "file_data": "data:application/pdf;base64,..."}}
     */
    public String chat(List<Map<String, Object>> messages) {
        if (!isConfigured()) {
            throw new IllegalStateException("OPENROUTER_API_KEY is not set (add it to the .env file)");
        }
        ChatResponse response = http.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("X-Title", "EasyInvoice")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("model", model, "messages", messages, "temperature", 0))
                .retrieve()
                .body(ChatResponse.class);
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new IllegalStateException("LLM returned no choices");
        }
        return response.choices().getFirst().message().content();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatResponse(List<Choice> choices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String role, String content) {
    }
}
