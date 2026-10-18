package edu.courseassist.core.corpus;

import io.github.jbellis.jvector.graph.*;
import io.github.jbellis.jvector.graph.similarity.DefaultSearchScoreProvider;
import io.github.jbellis.jvector.util.Bits;
import io.github.jbellis.jvector.vector.VectorSimilarityFunction;
import io.github.jbellis.jvector.vector.VectorizationProvider;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import static edu.courseassist.core.corpus.CorpusData.*;

/** Owns the immutable graph, exact float32 vectors and source mapping as one lifetime unit.
 * Library leases prevent close during concurrent queries; every query owns its GraphSearcher. */
public final class GenerationHandle implements AutoCloseable {
    private final Generation generation;
    private final List<Evidence> ordinals;
    private final Map<String, Evidence> byId;
    private final ListRandomAccessVectorValues vectors;
    private final ImmutableGraphIndex graph;
    private boolean closed;

    private GenerationHandle(Generation generation, List<Evidence> ordinals,
                             ListRandomAccessVectorValues vectors, ImmutableGraphIndex graph) {
        this.generation = generation; this.ordinals = List.copyOf(ordinals); this.vectors = vectors; this.graph = graph;
        Map<String, Evidence> map = new HashMap<>(); ordinals.forEach(e -> map.put(e.chunkId(), e)); byId = Map.copyOf(map);
    }
    public static GenerationHandle build(Generation generation) throws IOException {
        var support = VectorizationProvider.getInstance().getVectorTypeSupport();
        List<io.github.jbellis.jvector.vector.types.VectorFloat<?>> values = new ArrayList<>();
        List<Evidence> ordinals = new ArrayList<>();
        for (Derived d : generation.members()) for (Chunk c : d.chunks()) {
            validateVector(c.vector(), generation.settings().space().dimensions());
            values.add(support.createFloatVector(c.vector()));
            ordinals.add(new Evidence(generation.id(), c.id(), d.source(), c.page(), c.start(), c.end(), c.text(), 0));
        }
        var vectors = new ListRandomAccessVectorValues(values, generation.settings().space().dimensions());
        if (values.isEmpty()) return new GenerationHandle(generation, ordinals, vectors, null);
        var config = generation.settings().index();
        // Owned pool: never install global JVector executors or change IDE VM properties.
        try (var pool = new ForkJoinPool(1);
             var builder = GraphIndexBuilder.builder(vectors, VectorSimilarityFunction.COSINE, config.degree())
                     .withBeamWidth(config.beamWidth()).withNeighborOverflow(config.overflow()).withAlpha(config.alpha())
                     .withSimdExecutor(pool).withParallelExecutor(pool).build()) {
            return new GenerationHandle(generation, ordinals, vectors, builder.build(vectors));
        }
    }
    public Generation generation() { return generation; }
    public List<Evidence> search(float[] query, EmbeddingSpace space, SearchSettings settings) throws IOException {
        checkOpen();
        if (!generation.settings().space().equals(space)) throw new IllegalArgumentException("Query embedding space differs from generation");
        validateVector(query, space.dimensions());
        if (graph == null) return List.of();
        var q = VectorizationProvider.getInstance().getVectorTypeSupport().createFloatVector(query.clone());
        try (var searcher = new GraphSearcher(graph)) {
            var found = searcher.search(DefaultSearchScoreProvider.exact(q, VectorSimilarityFunction.COSINE, vectors),
                    Math.min(settings.topK(), ordinals.size()), Bits.ALL);
            List<Evidence> result = new ArrayList<>();
            for (var node : found.getNodes()) {
                if (node.node < 0 || node.node >= ordinals.size() || !Float.isFinite(node.score)) throw new IllegalStateException("Invalid graph result");
                if (node.score < settings.minimumScore()) continue;
                var e = ordinals.get(node.node);
                result.add(new Evidence(e.generationId(), e.chunkId(), e.source(), e.physicalPage(), e.startOffset(), e.endOffset(), e.text(), node.score));
            }
            return List.copyOf(result);
        }
    }
    public Evidence read(String chunkId) {
        checkOpen(); var evidence = byId.get(chunkId);
        if (evidence == null) throw new IllegalArgumentException("Evidence is outside the fixed generation");
        return evidence;
    }
    private void checkOpen() { if (closed) throw new IllegalStateException("Generation handle closed"); }
    @Override public void close() {
        if (!closed) {
            closed = true;
            try { if (graph != null) graph.close(); }
            catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }
    }
}
