package edu.courseassist.model;

import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import edu.courseassist.contract.ApplicationContracts.*;
import edu.courseassist.contract.CredentialUpdate.Slot;
import edu.courseassist.core.corpus.CorpusData.*;
import edu.courseassist.core.react.ReactContext;
import edu.courseassist.core.react.ReactData.*;
import edu.courseassist.core.session.*;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;
import java.time.Duration;
import java.util.*;

/** Explicit DeepSeek SSE / OpenAI embedding compatibility route. Construction performs no
 * network call. Actual endpoints/models remain user configuration and separately unverified. */
public final class ConfiguredRuntimeFactory implements ApplicationCoordinator.Factory {
    @Override public RuntimeResources create(ProviderConfiguration profiles,CredentialVault vault) throws Exception {
        if ("api.deepseek.com".equalsIgnoreCase(profiles.embedding().endpoint().getHost()))
            throw new InvalidEmbeddingConfiguration();
        return ModelClassLoading.call(()->createConfigured(profiles,vault));
    }
    public static final class InvalidEmbeddingConfiguration extends IllegalArgumentException {
        public InvalidEmbeddingConfiguration() { super("嵌入配置错误：DeepSeek 官方生成接口不能作为 embedding 服务；请配置支持 /embeddings 的模型和其实际维度"); }
    }
    public static final class ConfigurationProblem extends IllegalArgumentException {
        public enum Reason {
            GENERATION_KEY("生成 API key 未保存，请在设置中填写并保存生成凭据"),
            EMBEDDING_KEY("嵌入 API key 未保存，请在设置中填写并保存嵌入凭据"),
            GENERATION_ADDRESS("生成服务 Base URL 必须是有效 HTTP(S) 地址"),
            EMBEDDING_ADDRESS("嵌入服务 Base URL 必须是有效 HTTP(S) 地址"),
            DIMENSIONS("嵌入维度未填写，请按嵌入模型文档填写"),
            GENERATION_KEY_FORMAT("生成 API key 含空白、换行或非 ASCII 字符，请重新复制纯 key 后保存"),
            EMBEDDING_KEY_FORMAT("嵌入 API key 含空白、换行或非 ASCII 字符，请重新复制纯 key 后保存");
            final String message;
            Reason(String message) { this.message=message; }
        }
        public ConfigurationProblem(Reason reason) { super(reason.message); }
    }
    private RuntimeResources createConfigured(ProviderConfiguration profiles,CredentialVault vault) throws Exception {
        char[] generation=new char[0],embedding=new char[0];
        SpringAiTransport transport=null;OpenAIClientImpl openAi=null;
        try {
            generation=vault.read(Slot.GENERATION);embedding=vault.read(Slot.EMBEDDING);
            if(generation.length==0)throw new ConfigurationProblem(ConfigurationProblem.Reason.GENERATION_KEY);
            if(embedding.length==0)throw new ConfigurationProblem(ConfigurationProblem.Reason.EMBEDDING_KEY);
            for(char value:generation)if(value<33 || value>126)throw new ConfigurationProblem(ConfigurationProblem.Reason.GENERATION_KEY_FORMAT);
            for(char value:embedding)if(value<33 || value>126)throw new ConfigurationProblem(ConfigurationProblem.Reason.EMBEDDING_KEY_FORMAT);
            for(var profile:List.of(profiles.generation(),profiles.embedding())) {
                if(!Set.of("http","https").contains(profile.endpoint().getScheme())||profile.endpoint().getHost()==null)
                    throw new ConfigurationProblem(profile==profiles.generation()?ConfigurationProblem.Reason.GENERATION_ADDRESS:ConfigurationProblem.Reason.EMBEDDING_ADDRESS);
            }
            var g=profiles.generation();var e=profiles.embedding();
            int dimensions=e.embeddingDimensions().orElseThrow(()->new ConfigurationProblem(ConfigurationProblem.Reason.DIMENSIONS));
            transport=new SpringAiTransport(Duration.ofSeconds(10),Duration.ofSeconds(60));
            var model=new SpringAiCourseModel(transport.webClient().baseUrl(g.endpoint().toString())
                    .defaultHeader("Authorization","Bearer "+new String(generation)).build(),"/chat/completions",g.modelId(),Optional.empty(),true);
            openAi=new OpenAIClientImpl(ClientOptions.builder().baseUrl(e.endpoint().toString()).apiKey(new String(embedding)).maxRetries(0)
                    .httpClient(SpringAiOpenAiHttpClient.builder().timeout(Duration.ofSeconds(60)).build()).build());
            var embeddingModel=OpenAiEmbeddingModel.builder().openAiClient(openAi)
                    .options(OpenAiEmbeddingOptions.builder().model(e.modelId()).dimensions(dimensions)
                            .encodingFormat(OpenAiEmbeddingOptions.EncodingFormat.FLOAT).build()).build();
            var space=new EmbeddingSpace(e.provider(),e.endpoint(),e.modelId(),dimensions,"","","explicit-profile-v1");
            // Conservative local byte estimates and finite MVP limits. They are not claims about
            // the configured provider's context window/tokenizer; real API calibration is separate.
            var limits=new Limits(6,8,16000,32000,34816,2048,Duration.ofMinutes(2),256);
            var context=new ContextPlanner.Settings(32768,24576,1024,2,8000,Duration.ofSeconds(30),g.modelId());
            var ownedTransport=transport;var ownedOpenAi=openAi;
            return new RuntimeResources(model,CourseEmbeddings.springAi(embeddingModel,space),
                    new Settings(space,new Processing("pdf-port-v1",1200,120),new IndexSettings(16,100,1.5f,1.2f)),
                    new SearchSettings(5,0.65f),limits,context,ReactContext.utf8Budget(),ErrorPolicy.PRODUCT,
                    ()->{try{ownedOpenAi.close();}finally{ownedTransport.close();}});
        } catch(Exception | LinkageError failure) {
            if(openAi!=null)try{openAi.close();}catch(Exception | LinkageError cleanup){failure.addSuppressed(cleanup);}
            if(transport!=null)try{transport.close();}catch(Exception | LinkageError cleanup){failure.addSuppressed(cleanup);}
            throw failure;
        } finally {Arrays.fill(generation,'\0');Arrays.fill(embedding,'\0');}
    }
}
