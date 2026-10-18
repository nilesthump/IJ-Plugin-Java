package edu.courseassist.model;

import org.springframework.core.codec.ByteArrayDecoder;
import org.springframework.core.codec.StringDecoder;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.http.codec.ServerSentEventHttpMessageReader;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.json.JsonMapper;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/** Owned JDK HTTP transport with explicit JSON/text/SSE codecs. Default Spring format detection
 * can see incompatible YAML/XML libraries bundled by the IDE. No classpath-wide format probing.
 * Caller supplies measured timeouts; construction makes no request. Close on a background thread.
 */
public final class SpringAiTransport implements AutoCloseable {
    private final HttpClient http;
    private final Duration readTimeout;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public SpringAiTransport(Duration connectTimeout, Duration readTimeout) {
        if (readTimeout == null || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("Read timeout must be positive");
        }
        this.readTimeout = readTimeout;
        http = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    }

    public RestClient.Builder restClient() {
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(factory).messageConverters(List.of(
                new ByteArrayHttpMessageConverter(), new StringHttpMessageConverter(StandardCharsets.UTF_8),
                new JacksonJsonHttpMessageConverter(mapper)));
    }

    public WebClient.Builder webClient() {
        var decoder = new JacksonJsonDecoder(mapper);
        var strategies = ExchangeStrategies.builder().codecs(codecs -> {
            codecs.registerDefaults(false);
            codecs.customCodecs().register(new ByteArrayDecoder());
            codecs.customCodecs().register(StringDecoder.textPlainOnly());
            codecs.customCodecs().register(decoder);
            codecs.customCodecs().register(new JacksonJsonEncoder(mapper));
            codecs.customCodecs().register(new ServerSentEventHttpMessageReader(decoder));
        }).build();
        var connector = new JdkClientHttpConnector(http);
        connector.setReadTimeout(readTimeout);
        return WebClient.builder().clientConnector(connector).exchangeStrategies(strategies);
    }

    @Override public void close() { http.close(); }
}
