package edu.courseassist.model;

import edu.courseassist.core.react.ReactData.*;
import edu.courseassist.contract.ChatContracts.TokenUsage;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage.*;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionRequest;
import org.springframework.ai.deepseek.api.DeepSeekApi.FunctionTool;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Spring AI 2.0.1 request/protocol types with strict raw SSE termination. The stock high-level
 * DeepSeek response conversion can swallow conversion errors; this boundary retains original
 * failures and unknown finish reasons. It has no advisor, internal tool loop, memory or retry.
 * Only the explicitly configured DeepSeek-style wire contract is supported; real gateways remain unverified.
 * The application supplies an owned SpringAiTransport WebClient with credentials/timeouts, off EDT. */
public final class SpringAiCourseModel implements Model {
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private final WebClient client;
    private final String path, model;
    private final Optional<ChatCompletionRequest.Thinking> thinking;
    private final boolean includeUsage;
    public SpringAiCourseModel(WebClient client, String path, String model,
                               Optional<ChatCompletionRequest.Thinking> thinking, boolean includeUsage) {
        this.client = Objects.requireNonNull(client); this.path = Objects.requireNonNull(path); this.model = Objects.requireNonNull(model);
        this.thinking = Objects.requireNonNull(thinking); this.includeUsage = includeUsage;
        if (!path.startsWith("/") || path.startsWith("//") || path.contains("?") || path.contains("#") || model.isBlank())
            throw new IllegalArgumentException("Invalid explicit model/path");
    }
    @Override public Cancel subscribe(Request request, Listener listener) {
        var messages = request.messages().stream().map(message -> new ChatCompletionMessage(
                message.content(), Role.valueOf(message.role().toUpperCase(Locale.ROOT)), null,
                message.callId().isEmpty() ? null : message.callId(),
                message.calls().isEmpty() ? null : message.calls().stream().map(c -> new ToolCall(c.id(), "function",
                        new ChatCompletionFunction(c.name(), c.arguments()))).toList(), null,
                message.thinking().isEmpty() ? null : message.thinking())).toList();
        var tools = request.tools().stream().map(t -> new FunctionTool(new FunctionTool.Function(t.description(), t.name(), t.schema()))).toList();
        var wire = ChatCompletionRequest.builder().messages(messages).model(model).maxTokens(request.outputTokens())
                .stream(true).tools(tools.isEmpty() ? null : tools).toolChoice(tools.isEmpty() ? "none" : "auto")
                .thinking(thinking.orElse(null)).build();
        // Request types and serialization remain Spring AI; only the response adapter is strict.
        var body = JSON.valueToTree(wire);
        if (includeUsage) ((tools.jackson.databind.node.ObjectNode) body).set("stream_options", JSON.valueToTree(Map.of("include_usage", true)));
        AtomicBoolean done = new AtomicBoolean();
        var responseId = new String[]{null};
        Flux<String> events = client.post().uri(path).contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body).retrieve().bodyToFlux(String.class);
        var subscription = events.takeUntil(data -> data.equals("[DONE]"))
                .doOnNext(data -> {
                    if (data.equals("[DONE]")) { done.set(true); return; }
                    decode(data, responseId, listener);
                }).concatWith(Flux.defer(() -> done.get() ? Flux.empty() : Flux.error(new ProtocolFailure("SSE ended without [DONE]"))))
                .subscribe(ignored -> {}, listener::failed, listener::complete);
        return subscription::dispose;
    }
    private static void decode(String data, String[] responseId, Listener listener) {
        JsonNode node = JSON.readTree(data);
        if (!node.isObject() || node.has("error")) throw new ProtocolFailure("Invalid/error SSE frame");
        if (node.hasNonNull("id")) {
            String id = text(node.get("id"));
            if (responseId[0] == null) responseId[0] = id;
            else if (!responseId[0].equals(id)) throw new ProtocolFailure("Response identity changed");
        }
        JsonNode choices = node.get("choices");
        if (choices == null || !choices.isArray() || choices.size() > 1) throw new ProtocolFailure("Expected single-choice SSE");
        for (JsonNode choice : choices) {
            if (integer(choice.get("index")) != 0) throw new ProtocolFailure("Unexpected choice index");
            JsonNode delta = choice.get("delta");
            if (delta == null || !delta.isObject()) throw new ProtocolFailure("Missing delta object");
            if (delta.hasNonNull("role") && !text(delta.get("role")).equals("assistant")) throw new ProtocolFailure("Unexpected streaming role");
            if (delta.hasNonNull("content")) listener.frame(new Body(text(delta.get("content"))));
            if (delta.hasNonNull("reasoning_content")) listener.frame(new Thinking(text(delta.get("reasoning_content"))));
            if (delta.hasNonNull("tool_calls")) {
                if (!delta.get("tool_calls").isArray()) throw new ProtocolFailure("Invalid tool delta");
                for (JsonNode tool : delta.get("tool_calls")) {
                    if (tool.hasNonNull("type") && !text(tool.get("type")).equals("function")) throw new ProtocolFailure("Unsupported tool type");
                    JsonNode function = tool.get("function");
                    if (function == null || !function.isObject()) throw new ProtocolFailure("Invalid function delta");
                    listener.frame(new ToolDelta(integer(tool.get("index")), optionalText(tool, "id"), optionalText(function, "name"), optionalText(function, "arguments")));
                }
            }
            if (choice.hasNonNull("finish_reason")) listener.frame(new End(text(choice.get("finish_reason"))));
        }
        if (node.hasNonNull("usage")) {
            JsonNode usage = node.get("usage");
            if (!usage.isObject() || !usage.hasNonNull("prompt_tokens") || !usage.hasNonNull("completion_tokens"))
                throw new ProtocolFailure("Incomplete usage");
            listener.frame(new Usage(new TokenUsage(count(usage.get("prompt_tokens")), count(usage.get("completion_tokens")))));
        }
    }
    private static long count(JsonNode value) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) throw new ProtocolFailure("Invalid count");
        return value.longValue();
    }
    private static int integer(JsonNode value) {
        long n = count(value); if (n > Integer.MAX_VALUE) throw new ProtocolFailure("Invalid index"); return (int)n;
    }
    private static String text(JsonNode value) { if (!value.isString()) throw new ProtocolFailure("Expected string"); return value.asString(); }
    private static String optionalText(JsonNode node, String field) { return node.hasNonNull(field) ? text(node.get(field)) : ""; }
}
