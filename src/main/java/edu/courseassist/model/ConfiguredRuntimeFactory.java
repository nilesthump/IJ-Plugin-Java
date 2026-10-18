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
        return ModelClassLoading.call(()->createConfigured(profiles,vault));
    }
    private RuntimeResources createConfigured(ProviderConfiguration profiles,CredentialVault vault) throws Exception {
        char[] generation=new char[0],embedding=new char[0];
        SpringAiTransport transport=null;OpenAIClientImpl openAi=null;
        try {
            generation=vault.read(Slot.GENERATION);embedding=vault.read(Slot.EMBEDDING);
            if(generation.length==0||embedding.length==0)throw new IllegalArgumentException("生成与嵌入凭据均需配置");
            for(var profile:List.of(profiles.generation(),profiles.embedding())) {
                if(!Set.of("http","https").contains(profile.endpoint().getScheme())||profile.endpoint().getHost()==null)
                    throw new IllegalArgumentException("Provider endpoint must be an HTTP(S) base URL");
            }
            var g=profiles.generation();var e=profiles.embedding();
            int dimensions=e.embeddingDimensions().orElseThrow(()->new IllegalArgumentException("Embedding dimensions must be configured explicitly"));
            transport=new SpringAiTransport(Duration.ofSeconds(10),Duration.ofSeconds(60));
            var model=new SpringAiCourseModel(transport.webClient().baseUrl(g.endpoint().toString())
                    .defaultHeader("Authorization","Bearer "+new String(generation)).build(),"/chat/completions",g.modelId(),Optional.empty(),true);
            openAi=new OpenAIClientImpl(ClientOptions.builder().baseUrl(e.endpoint().toString()).apiKey(new String(embedding)).maxRetries(0)
                    .httpClient(SpringAiOpenAiHttpClient.builder().timeout(Duration.ofSeconds(60)).build()).build());
            var embeddingModel=OpenAiEmbeddingModel.builder().openAiClient(openAi)
                    .options(OpenAiEmbeddingOptions.builder().model(e.modelId()).dimensions(dimensions).build()).build();
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
        } catch(Exception failure) {
            if(openAi!=null)try{openAi.close();}catch(Exception cleanup){failure.addSuppressed(cleanup);}
            if(transport!=null)try{transport.close();}catch(Exception cleanup){failure.addSuppressed(cleanup);}
            throw failure;
        } finally {Arrays.fill(generation,'\0');Arrays.fill(embedding,'\0');}
    }
}
