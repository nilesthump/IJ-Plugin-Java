package edu.courseassist.contract;

import edu.courseassist.contract.CommonContracts.Notice;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static edu.courseassist.contract.CommonContracts.requireId;
import static edu.courseassist.contract.CommonContracts.requireRevision;

/** Application-wide configuration and material actions have no implicit current-session target. */
public final class ApplicationContracts {
    private ApplicationContracts() {}

    public enum WorkState { OFF, STARTING, ON, STOPPING }
    public enum CorpusState { EMPTY, RESTORING, READY, FAILED }
    public enum MaterialStatus { PREPARING, READY, FAILED, REMOVED }
    public enum CredentialStatus { MISSING, CONFIGURED, NOT_REQUIRED }

    /** Non-secret fields only. Model identity and vector dimensions are actual deployment choices. */
    public record ProviderProfile(String profileId, String provider, URI endpoint, String modelId,
                                   Optional<Integer> embeddingDimensions) {
        public ProviderProfile {
            requireId(profileId, "profileId"); requireId(provider, "provider");
            Objects.requireNonNull(endpoint, "endpoint"); requireId(modelId, "modelId");
            if (!endpoint.isAbsolute() || endpoint.getUserInfo() != null || endpoint.getQuery() != null
                    || endpoint.getFragment() != null) {
                throw new IllegalArgumentException("Endpoint must be absolute and contain no embedded credentials/query");
            }
            embeddingDimensions = Objects.requireNonNull(embeddingDimensions, "embeddingDimensions");
            if (embeddingDimensions.isPresent() && embeddingDimensions.get() < 1) {
                throw new IllegalArgumentException("Embedding dimension must be positive");
            }
        }
    }

    public record ProviderConfiguration(ProviderProfile generation, ProviderProfile embedding) {
        public ProviderConfiguration {
            Objects.requireNonNull(generation, "generation"); Objects.requireNonNull(embedding, "embedding");
        }
    }
    public record ProfileView(ProviderProfile profile, CredentialStatus credentialStatus) {
        public ProfileView { Objects.requireNonNull(profile, "profile"); Objects.requireNonNull(credentialStatus, "credentialStatus"); }
    }

    /** Expected library state, including the explicit no-generation state. */
    public record CorpusBase(long revision, Optional<String> generationId) {
        public CorpusBase { requireRevision(revision); generationId = Objects.requireNonNull(generationId, "generationId"); }
    }

    public record MaterialView(String documentId, String name, long materialRevision,
                                Optional<String> sourceVersionId, MaterialStatus status,
                                Optional<Integer> physicalPageCount, Instant changedAt,
                                Optional<String> statusMessage) {
        public MaterialView {
            requireId(documentId, "documentId"); Objects.requireNonNull(name, "name"); requireRevision(materialRevision);
            sourceVersionId = Objects.requireNonNull(sourceVersionId, "sourceVersionId");
            Objects.requireNonNull(status, "status"); physicalPageCount = Objects.requireNonNull(physicalPageCount, "physicalPageCount");
            Objects.requireNonNull(changedAt, "changedAt"); statusMessage = Objects.requireNonNull(statusMessage, "statusMessage");
            if (physicalPageCount.isPresent() && physicalPageCount.get() < 0) throw new IllegalArgumentException("Negative page count");
        }
    }

    /** Capture this value when the user starts an action; later selected-row changes cannot retarget it. */
    public record MaterialTarget(String documentId, Optional<String> expectedSourceVersionId, long expectedMaterialRevision) {
        public MaterialTarget {
            requireId(documentId, "documentId"); expectedSourceVersionId = Objects.requireNonNull(expectedSourceVersionId, "expectedSourceVersionId");
            requireRevision(expectedMaterialRevision);
        }
    }

    public record ApplicationViewState(long revision, WorkState workState, long configRevision,
                                        long runtimeEpoch, Optional<ProfileView> generationProfile,
                                        Optional<ProfileView> embeddingProfile, CorpusState corpusState,
                                        CorpusBase corpusBase, List<MaterialView> materials,
                                        boolean hasUnsavedState, List<Notice> notices) {
        public ApplicationViewState {
            requireRevision(revision); Objects.requireNonNull(workState, "workState"); requireRevision(configRevision); requireRevision(runtimeEpoch);
            generationProfile = Objects.requireNonNull(generationProfile, "generationProfile");
            embeddingProfile = Objects.requireNonNull(embeddingProfile, "embeddingProfile");
            Objects.requireNonNull(corpusState, "corpusState"); Objects.requireNonNull(corpusBase, "corpusBase");
            materials = List.copyOf(materials); notices = List.copyOf(notices);
        }
    }

    public record ApplicationEventMeta(String commandId, long expectedAppStateRevision) {
        public ApplicationEventMeta { requireId(commandId, "commandId"); requireRevision(expectedAppStateRevision); }
    }
    public sealed interface ApplicationEvent permits EnablePlugin, DisablePlugin, UpdateProviderConfig,
            ImportCoursePdf, ReplaceCourseMaterial, RemoveCourseMaterial { ApplicationEventMeta meta(); }

    public record EnablePlugin(ApplicationEventMeta meta) implements ApplicationEvent {
        public EnablePlugin { Objects.requireNonNull(meta, "meta"); }
    }
    public record DisablePlugin(ApplicationEventMeta meta) implements ApplicationEvent {
        public DisablePlugin { Objects.requireNonNull(meta, "meta"); }
    }
    /** credentialUpdateRef is a short-lived opaque capability, not a key or a durable setting. */
    public record UpdateProviderConfig(ApplicationEventMeta meta, long expectedConfigRevision,
                                       ProviderConfiguration configuration, Optional<String> credentialUpdateRef) implements ApplicationEvent {
        public UpdateProviderConfig {
            Objects.requireNonNull(meta, "meta"); requireRevision(expectedConfigRevision);
            Objects.requireNonNull(configuration, "configuration"); credentialUpdateRef = Objects.requireNonNull(credentialUpdateRef, "credentialUpdateRef");
        }
    }
    public record ImportCoursePdf(ApplicationEventMeta meta, Path selectedFile, CorpusBase expectedCorpus) implements ApplicationEvent {
        public ImportCoursePdf { Objects.requireNonNull(meta, "meta"); inputFile(selectedFile); Objects.requireNonNull(expectedCorpus, "expectedCorpus"); }
    }
    public record ReplaceCourseMaterial(ApplicationEventMeta meta, MaterialTarget target, Path selectedFile,
                                         CorpusBase expectedCorpus) implements ApplicationEvent {
        public ReplaceCourseMaterial {
            Objects.requireNonNull(meta, "meta"); Objects.requireNonNull(target, "target"); inputFile(selectedFile); Objects.requireNonNull(expectedCorpus, "expectedCorpus");
        }
    }
    public record RemoveCourseMaterial(ApplicationEventMeta meta, MaterialTarget target,
                                        CorpusBase expectedCorpus) implements ApplicationEvent {
        public RemoveCourseMaterial { Objects.requireNonNull(meta, "meta"); Objects.requireNonNull(target, "target"); Objects.requireNonNull(expectedCorpus, "expectedCorpus"); }
    }
    private static void inputFile(Path path) {
        if (!Objects.requireNonNull(path, "selectedFile").isAbsolute()) throw new IllegalArgumentException("Selected file path must be absolute");
    }
}
