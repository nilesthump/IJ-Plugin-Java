package edu.courseassist.core.corpus;

import edu.courseassist.contract.ApplicationContracts.*;
import edu.courseassist.contract.PdfContracts.*;
import edu.courseassist.contract.PdfReader;
import edu.courseassist.model.CourseEmbeddings;
import edu.courseassist.storage.CourseRepository;
import edu.courseassist.storage.ManagedPdfStore;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import static edu.courseassist.core.corpus.CorpusData.*;

/** Application-owned corpus. Public operations schedule disk/network/graph work off the caller.
 * Runtime permission is supplied by the owning application epoch; never inferred from UI state.
 * Production ApplicationController wiring belongs to group 4, not a synthetic controller here. */
public final class CourseLibrary implements AutoCloseable {
    @FunctionalInterface public interface GraphFactory { GenerationHandle build(Generation generation) throws Exception; }
    private final CourseRepository repository;
    private final ManagedPdfStore files;
    private final Supplier<PdfReader> reader;
    private final Executor executor;
    private final GraphFactory graphs;
    private final Object gate = new Object();
    private final Object fileGate = new Object();
    private final Map<String, Entry> entries = new HashMap<>();
    private final Set<Lease> leases = new HashSet<>();
    private final Set<Job<?>> jobs = new HashSet<>();
    private CompletableFuture<Void> initialized;
    private CorpusBase active;
    private volatile boolean closed;

    public CourseLibrary(CourseRepository repository, ManagedPdfStore files, Supplier<PdfReader> reader, Executor executor) {
        this(repository, files, reader, executor, GenerationHandle::build);
    }
    public CourseLibrary(CourseRepository repository, ManagedPdfStore files, Supplier<PdfReader> reader, Executor executor, GraphFactory graphs) {
        this.repository = Objects.requireNonNull(repository); this.files = Objects.requireNonNull(files);
        this.reader = Objects.requireNonNull(reader); this.executor = Objects.requireNonNull(executor); this.graphs = Objects.requireNonNull(graphs);
    }
    public CompletableFuture<Void> initialize() {
        synchronized (gate) {
            ensureOpen();
            if (initialized == null) initialized = CompletableFuture.runAsync(() -> {
                repository.initialize();
                synchronized (fileGate) { finishDeletions(); }
                var base = repository.base();
                synchronized (gate) { ensureOpen(); active = base; base.generationId().ifPresent(this::entry); }
            }, executor);
            // A consumer may cancel this dependent future; the shared restore remains owned by the application.
            return initialized.copy();
        }
    }
    public CompletableFuture<Snapshot> snapshot() {
        return initialize().thenApplyAsync(ignored -> {
            synchronized (gate) {
                ensureOpen(); Availability status = Availability.EMPTY;
                if (active.generationId().isPresent()) {
                    var future = entry(active.generationId().get()).future;
                    status = !future.isDone() ? Availability.RESTORING : future.isCompletedExceptionally() ? Availability.FAILED : Availability.READY;
                }
                return new Snapshot(active, status, repository.materials(), repository.jobs());
            }
        }, executor);
    }
    /** Called at attempt creation after initialize completes. Includes an explicit fixed no-library state. */
    public Lease openAttempt() {
        synchronized (gate) {
            ensureOpen(); if (active == null) throw new IllegalStateException("Initialize corpus before opening an attempt");
            Entry e = active.generationId().map(this::entry).orElse(null);
            if (e != null) e.references++;
            Lease lease = new Lease(active, e); leases.add(lease); return lease;
        }
    }
    public Job<Change> importPdf(Path selected, String name, CorpusBase base, Settings settings, CourseEmbeddings embeddings, BooleanSupplier permitted) {
        return change(selected, name, base, Optional.empty(), settings, embeddings, permitted);
    }
    public Job<Change> replacePdf(Path selected, String name, CorpusBase base, MaterialTarget target, Settings settings, CourseEmbeddings embeddings, BooleanSupplier permitted) {
        return change(selected, name, base, Optional.of(target), settings, embeddings, permitted);
    }
    private Job<Change> change(Path selected, String name, CorpusBase base, Optional<MaterialTarget> target,
                               Settings settings, CourseEmbeddings embeddings, BooleanSupplier permitted) {
        Objects.requireNonNull(selected); Objects.requireNonNull(name); Objects.requireNonNull(base); Objects.requireNonNull(settings);
        if (!settings.space().equals(embeddings.space())) throw new IllegalArgumentException("Embedding client/space mismatch");
        return start(permitted, job -> {
            job.check();
            CourseRepository.Reservation reservation;
            // Serialize blob install/registration against explicit cleanup. Copying does not hold a DB transaction.
            synchronized (fileGate) {
                job.check(); var blob = files.copy(selected); job.check();
                synchronized (gate) {
                    job.check(); reservation = repository.reserve(job.id, name, blob, base, target, settings);
                    job.sources = Set.of(reservation.source().versionId());
                    job.registered = reservation.duplicate().isEmpty();
                }
            }
            Source source = reservation.source();
            if (reservation.duplicate().isPresent()) {
                files.verify(repository.blob(source.versionId()));
                return reservation.duplicate().get();
            }
            job.sources = Set.of(source.versionId()); job.check();
            List<Derived> members = members(base, job);
            // Ordinary single-document operations may not silently mix or re-embed other materials.
            for (Derived d : members) if (!d.settings().derivationKey().equals(settings.derivationKey()))
                throw new Conflict("Corpus processing/space changed; use explicit full reindex");
            Optional<Derived> reused = repository.findDerived(source.versionId(), settings);
            Derived derived = reused.isPresent() ? reused.get() : derive(source, settings, embeddings, job);
            if (target.isPresent() && target.get().expectedSourceVersionId().equals(Optional.of(source.versionId())) && reused.isPresent()) {
                synchronized (gate) {
                    job.check(); if (!active.equals(base)) throw new Conflict("Corpus base changed");
                    // Even an unchanged response validates the target's latest material revision.
                    var current = repository.materials().stream().filter(m -> m.documentId().equals(source.documentId())).findFirst().orElseThrow();
                    if (current.materialRevision() != target.get().expectedMaterialRevision()) throw new Conflict("Material revision changed");
                    repository.fail(job.id, "UNCHANGED");
                    return new Change(Disposition.UNCHANGED, source.documentId(), Optional.of(source.versionId()), active);
                }
            }
            members.removeIf(d -> d.source().documentId().equals(source.documentId())); members.add(derived);
            Generation generation = new Generation(id(), settings, members);
            return publish(job, generation, base, target, Optional.of(source), false);
        });
    }
    public Job<Change> remove(CorpusBase base, MaterialTarget target, BooleanSupplier permitted) {
        return start(permitted, job -> {
            synchronized (fileGate) {
                Change change;
                if (target.expectedSourceVersionId().isEmpty()) {
                    synchronized (gate) {
                        job.check(); if (!active.equals(base)) throw new Conflict("Corpus base changed");
                        Set<String> sources = repository.documentSources(target.documentId());
                        CorpusBase committed = repository.removeUnpublished(base, target);
                        active = committed; job.committed = true;
                        revokeDeletedSources(sources, job);
                        change = new Change(Disposition.PUBLISHED, target.documentId(), Optional.empty(), committed);
                    }
                } else {
                    List<Derived> members = members(base, job);
                    Settings settings = repository.load(base.generationId().orElseThrow(() -> new Conflict("No corpus to remove from"))).settings();
                    members.removeIf(d -> d.source().documentId().equals(target.documentId()));
                    change = publish(job, new Generation(id(), settings, members), base, Optional.of(target), Optional.empty(), true);
                }
                finishDeletions();
                return change;
            }
        });
    }
    private void finishDeletions() {
        try {
            for (String md5 : repository.deletingBlobs()) { files.delete(md5); repository.finishBlobDelete(md5); }
        } catch (java.io.IOException failure) { throw new CompletionException(failure); }
    }
    /** gate held; immutable graph readers are revoked before their pending results can escape. */
    private void revokeDeletedSources(Set<String> sources, Job<?> removal) {
        for (Job<?> candidate : jobs) if (candidate != removal && !Collections.disjoint(candidate.sources, sources)) candidate.removed = true;
        for (Entry entry : entries.values()) if (!Collections.disjoint(entry.sources, sources)) entry.removed = true;
        for (Lease lease : List.copyOf(leases)) if (lease.entry != null && lease.entry.removed) lease.close();
    }
    /** Explicit whole-active-set reindex for a processing/model change. Never reuse equal-dimension spaces. */
    public Job<Change> reindex(CorpusBase base, Settings settings, CourseEmbeddings embeddings, BooleanSupplier permitted) {
        if (!settings.space().equals(embeddings.space())) throw new IllegalArgumentException("Embedding client/space mismatch");
        return start(permitted, job -> {
            List<Derived> previous = members(base, job);
            List<Derived> members = new ArrayList<>();
            for (Derived d : previous) {
                job.check(); var reused = repository.findDerived(d.source().versionId(), settings);
                members.add(reused.isPresent() ? reused.get() : derive(d.source(), settings, embeddings, job));
            }
            return publish(job, new Generation(id(), settings, members), base, Optional.empty(), Optional.empty(), false);
        });
    }
    private Derived derive(Source source, Settings settings, CourseEmbeddings embeddings, Job<?> job) throws Exception {
        job.check(); var blob = repository.blob(source.versionId()); files.verify(blob);
        var request = new PdfReadRequest(source.documentId(), source.versionId(), files.path(blob.md5()), blob.md5());
        PdfReadResult pdf = reader.get().read(request);
        if (!pdf.source().equals(request)) throw new IllegalArgumentException("PDF adapter changed fixed source identity");
        // Verify the adapter did not alter the owned fixed bytes.
        files.verify(blob); job.check(); List<Chunk> chunks = new ArrayList<>();
        for (var slice : PageChunker.split(pdf.pages(), settings.processing())) {
            job.check(); float[] vector = embeddings.document(slice.text()); job.check();
            chunks.add(new Chunk(id(), slice.physicalPage(), slice.start(), slice.end(), slice.text(), vector, settings.space().dimensions()));
        }
        synchronized (gate) {
            job.check(); return repository.saveDerived(new Derived(source, id(), settings, pdf.pages(), chunks));
        }
    }
    private List<Derived> members(CorpusBase base, Job<?> job) {
        synchronized (gate) {
            ensureOpen(); if (!active.equals(base)) throw new Conflict("Corpus base changed");
            List<Derived> members = new ArrayList<>(base.generationId().map(repository::load).map(Generation::members).orElse(List.of()));
            Set<String> pinned = new HashSet<>(job.sources); members.forEach(d -> pinned.add(d.source().versionId())); job.sources = Set.copyOf(pinned);
            return members;
        }
    }
    private Change publish(Job<?> job, Generation generation, CorpusBase base, Optional<MaterialTarget> target, Optional<Source> changed, boolean remove) throws Exception {
        job.check(); GenerationHandle handle = graphs.build(generation);
        boolean published = false;
        try {
            if (!handle.generation().id().equals(generation.id()) || !handle.generation().dataKey().equals(generation.dataKey()))
                throw new IllegalStateException("Graph handle does not match candidate generation");
            synchronized (gate) {
                job.check(); if (!active.equals(base)) throw new Conflict("Corpus base changed; candidate was not published");
                Set<String> deletedSources = remove ? repository.documentSources(target.orElseThrow().documentId()) : Set.of();
                CorpusBase committed = repository.publish(generation, base, target, changed, remove, job.registered ? Optional.of(job.id) : Optional.empty());
                Entry newEntry = new Entry(generation.id(), generation.sourceIds(), CompletableFuture.completedFuture(handle));
                entries.put(generation.id(), newEntry); CorpusBase previous = active; active = committed; job.committed = true; published = true;
                if (remove) revokeDeletedSources(deletedSources, job);
                previous.generationId().ifPresent(this::discardUnused);
                return new Change(Disposition.PUBLISHED, changed.map(Source::documentId).orElseGet(() -> target.map(MaterialTarget::documentId).orElse("corpus")), changed.map(Source::versionId), committed);
            }
        } finally { if (!published) handle.close(); }
    }
    private Entry entry(String id) {
        return entries.computeIfAbsent(id, key -> new Entry(key, repository.generationSources(key), CompletableFuture.supplyAsync(() -> {
            try {
                var generation = repository.load(key);
                for (Derived d : generation.members()) files.verify(repository.blob(d.source().versionId()));
                var handle = graphs.build(generation);
                if (!handle.generation().id().equals(key) || !handle.generation().dataKey().equals(generation.dataKey())) {
                    handle.close(); throw new IllegalStateException("Restored graph handle does not match durable generation");
                }
                return handle;
            } catch (Exception failure) { throw new CompletionException(failure); }
        }, executor)));
    }
    private void discardUnused(String id) {
        Entry e = entries.get(id);
        if (e != null && e.references == 0 && (closed || !active.generationId().equals(Optional.of(id)))) {
            entries.remove(id); e.future.thenAccept(GenerationHandle::close);
        }
    }
    private final class Entry {
        final String id; final Set<String> sources; final CompletableFuture<GenerationHandle> future; int references; boolean removed;
        Entry(String id, Set<String> sources, CompletableFuture<GenerationHandle> future) { this.id = id; this.sources = sources; this.future = future; }
    }
    public final class Lease implements AutoCloseable {
        private final CorpusBase base;
        private final Entry entry;
        private final Set<CompletableFuture<?>> pending = new HashSet<>();
        private boolean released;
        private Lease(CorpusBase base, Entry entry) { this.base = base; this.entry = entry; }
        public CorpusBase base() { return base; }
        public CompletableFuture<Boolean> hasMaterials() { return use(h -> h != null && !h.generation().members().isEmpty()); }
        public CompletableFuture<List<Evidence>> search(String exactQuery, CourseEmbeddings embeddings, SearchSettings settings, BooleanSupplier permitted) {
            return use(h -> {
                if (h == null || h.generation().members().isEmpty()) return List.of();
                if (!h.generation().settings().space().equals(embeddings.space())) throw new IllegalArgumentException("Query model differs from fixed corpus space");
                checkPermit(permitted); float[] q = embeddings.query(exactQuery); checkPermit(permitted);
                var result = h.search(q, embeddings.space(), settings); checkPermit(permitted); return result;
            });
        }
        public CompletableFuture<Evidence> read(String chunkId) {
            return use(h -> { if (h == null) throw new IllegalArgumentException("No fixed corpus evidence"); return h.read(chunkId); });
        }
        /** Called by future answer persistence while this lease still pins the source versions. */
        public CompletableFuture<Void> retainSources(String owner, Set<String> sourceIds) {
            return use(h -> {
                if (h == null || !h.generation().sourceIds().containsAll(sourceIds)) throw new IllegalArgumentException("References outside fixed generation");
                synchronized (gate) { checkLease(); repository.retain(owner, sourceIds); }
                return null;
            });
        }
        @FunctionalInterface private interface Read<T> { T apply(GenerationHandle h) throws Exception; }
        private <T> CompletableFuture<T> use(Read<T> read) {
            CompletableFuture<T> result = new CompletableFuture<>();
            synchronized (gate) { checkLease(); pending.add(result); if (entry != null) entry.references++; }
            CompletableFuture<GenerationHandle> ready = entry == null ? CompletableFuture.completedFuture(null) : entry.future;
            ready.whenComplete((h, failure) -> {
                Runnable action = () -> {
                try {
                    synchronized (gate) { checkLease(); if (result.isCancelled()) throw new CancellationException("Query wait cancelled"); }
                    if (failure != null) throw new CompletionException(failure);
                    T value = read.apply(h);
                    synchronized (gate) { checkLease(); pending.remove(result); result.complete(value); }
                } catch (Throwable problem) { result.completeExceptionally(problem); }
                finally {
                    synchronized (gate) { pending.remove(result); if (entry != null) { entry.references--; discardUnused(entry.id); } }
                }
                };
                try { executor.execute(action); }
                catch (RuntimeException rejected) {
                    result.completeExceptionally(rejected);
                    synchronized (gate) { pending.remove(result); if (entry != null) { entry.references--; discardUnused(entry.id); } }
                }
            });
            return result;
        }
        private void checkLease() { ensureOpen(); if (released || entry != null && entry.removed) throw new CancellationException("Attempt corpus lease released or material deleted"); }
        @Override public void close() {
            List<CompletableFuture<?>> cancelled;
            synchronized (gate) {
                if (released) return; released = true; leases.remove(this);
                cancelled = List.copyOf(pending); pending.clear();
                if (entry != null) { entry.references--; discardUnused(entry.id); }
            }
            for (var future : cancelled) future.completeExceptionally(new CancellationException("Attempt corpus lease released"));
        }
    }
    public CompletableFuture<CleanupPreview> previewCleanup() {
        return initialize().thenApplyAsync(v -> {
            synchronized (fileGate) {
                synchronized (gate) {
                    ensureOpen(); Set<String> sources = repository.cleanable(pinned());
                    Set<String> blobs = new HashSet<>(repository.previewBlobs(sources));
                    try { Set<String> orphans = new HashSet<>(files.files()); orphans.removeAll(repository.knownBlobs()); blobs.addAll(orphans); }
                    catch (java.io.IOException failure) { throw new CompletionException(failure); }
                    return new CleanupPreview(active, sources, blobs);
                }
            }
        }, executor);
    }
    public CompletableFuture<Void> cleanup(CleanupPreview preview) {
        return initialize().thenRunAsync(() -> {
            synchronized (fileGate) {
                Set<String> deleting;
                synchronized (gate) { ensureOpen(); deleting = repository.retire(preview, pinned()); }
                try {
                    for (String md5 : deleting) { files.delete(md5); repository.finishBlobDelete(md5); }
                    // A file installed before a failed DB commit is also eligible only through this explicit action.
                    Set<String> orphans = new HashSet<>(files.files()); orphans.removeAll(repository.knownBlobs());
                    orphans.retainAll(preview.blobMd5s());
                    for (String md5 : orphans) files.delete(md5);
                } catch (java.io.IOException failure) { throw new CompletionException(failure); }
            }
        }, executor);
    }
    public static final class MaterialDeleted extends RuntimeException {
        public MaterialDeleted() { super("资料已删除"); }
    }
    public CompletableFuture<Path> sourcePath(String sourceId) {
        return initialize().thenApplyAsync(v -> {
            synchronized (fileGate) {
                ManagedPdfStore.Blob blob;
                synchronized (gate) { ensureOpen(); if (!repository.hasSource(sourceId)) throw new MaterialDeleted(); blob = repository.blob(sourceId); }
                try { files.verify(blob); return files.path(blob.md5()); }
                catch (java.io.IOException failure) { throw new CompletionException(failure); }
            }
        }, executor);
    }
    public CompletableFuture<Void> releaseReferences(String owner) {
        return initialize().thenRunAsync(() -> { synchronized (gate) { ensureOpen(); repository.release(owner); } }, executor);
    }
    private Set<String> pinned() {
        Set<String> pinned = new HashSet<>();
        for (Entry e : entries.values()) if (e.references > 0 && !e.removed) pinned.addAll(e.sources);
        for (Job<?> job : jobs) pinned.addAll(job.sources);
        return pinned;
    }
    @FunctionalInterface private interface Work<T> { T run(Job<T> job) throws Exception; }
    private <T> Job<T> start(BooleanSupplier permitted, Work<T> work) {
        Job<T> job = new Job<>(Objects.requireNonNull(permitted));
        CompletableFuture<Void> initialization;
        synchronized (gate) { ensureOpen(); initialization = initialize(); jobs.add(job); }
        initialization.whenComplete((v, initFailure) -> {
            Runnable action = () -> {
            Throwable settlementFailure = null;
            try {
                if (initFailure != null) throw new CompletionException(initFailure);
                job.check(); T value = work.run(job); boolean deliver;
                synchronized (gate) { if (!job.committed) job.check(); deliver = job.claimResult(); }
                if (deliver) job.result.complete(value);
            } catch (Throwable failure) {
                if (job.registered && !job.committed) {
                    try { repository.fail(job.id, job.cancelled || isCancellation(failure) ? "CANCELLED" : "FAILED"); }
                    catch (Throwable saveFailure) { failure.addSuppressed(saveFailure); settlementFailure = saveFailure; }
                }
                boolean deliver; synchronized (gate) { deliver = job.claimResult(); }
                if (deliver) job.result.completeExceptionally(failure);
            } finally {
                synchronized (gate) { jobs.remove(job); }
                if (settlementFailure == null) job.settled.complete(null);
                else job.settled.completeExceptionally(settlementFailure);
            }
            };
            try { executor.execute(action); }
            catch (RuntimeException rejected) {
                boolean deliver; synchronized (gate) { jobs.remove(job); deliver = job.claimResult(); }
                if (deliver) job.result.completeExceptionally(rejected);
                job.settled.complete(null);
            }
        });
        return job;
    }
    public final class Job<T> implements AutoCloseable {
        private final String id = CourseLibrary.id(); private final BooleanSupplier permitted;
        private final CompletableFuture<T> result = new CompletableFuture<>();
        private final CompletableFuture<Void> settled = new CompletableFuture<>();
        private volatile boolean cancelled, removed;
        private boolean registered, committed, resultClaimed;
        private volatile Set<String> sources = Set.of();
        private Job(BooleanSupplier permitted) { this.permitted = permitted; }
        public String id() { return id; }
        public CompletionStage<T> completion() { return result.minimalCompletionStage(); }
        public CompletionStage<Void> settled() { return settled.minimalCompletionStage(); }
        private boolean claimResult() { if (resultClaimed) return false; resultClaimed = true; return true; }
        private void check() { if (removed) throw new Conflict("Material deleted"); if (cancelled || closed) throw new CancellationException("Corpus operation invalidated"); checkPermit(permitted); }
        /** Linearizes against the short publication commit; never interrupts JDBC or a shared restore. */
        public void cancel() {
            boolean deliver;
            synchronized (gate) { deliver = !committed && claimResult(); if (deliver) cancelled = true; }
            if (deliver) result.completeExceptionally(new CancellationException("Corpus operation cancelled"));
        }
        /** Application stop's local durable boundary. Remote embedding may still physically be
         * returning, but publication is revoked before this save and can never resume on a new epoch. */
        public void cancelAndRecord() {
            cancel();
            synchronized(gate) {
                if(cancelled && registered && !committed) repository.fail(id,"CANCELLED");
            }
        }
        @Override public void close() { cancel(); }
    }
    private static boolean isCancellation(Throwable t) {
        for (Throwable cause = t; cause != null; cause = cause.getCause()) {
            if (cause instanceof CancellationException) return true;
            for (Class<?> type = cause.getClass(); type != null; type = type.getSuperclass())
                if (type.getName().equals("com.intellij.openapi.progress.ProcessCanceledException")) return true;
        }
        return false;
    }
    private static void checkPermit(BooleanSupplier permitted) { if (!permitted.getAsBoolean()) throw new CancellationException("Application runtime epoch invalidated"); }
    private static String id() { return UUID.randomUUID().toString(); }
    private void ensureOpen() { if (closed) throw new CancellationException("Course library closed"); }
    @Override public void close() {
        List<Job<?>> cancelledJobs; List<Lease> cancelledLeases;
        synchronized (gate) {
            if (closed) return; closed = true;
            cancelledJobs = List.copyOf(jobs); cancelledLeases = List.copyOf(leases);
        }
        for (var job : cancelledJobs) job.cancel();
        for (var lease : cancelledLeases) lease.close();
        synchronized (gate) {
            for (String id : List.copyOf(entries.keySet())) discardUnused(id);
        }
    }
}
