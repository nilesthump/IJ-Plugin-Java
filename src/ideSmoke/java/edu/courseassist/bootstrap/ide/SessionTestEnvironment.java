package edu.courseassist.bootstrap.ide;

import edu.courseassist.contract.*;
import edu.courseassist.contract.ApplicationContracts.*;
import edu.courseassist.contract.ChatContracts.*;
import edu.courseassist.contract.CaptureContracts.*;
import edu.courseassist.contract.CommonContracts.*;
import edu.courseassist.core.*;
import edu.courseassist.core.corpus.*;
import edu.courseassist.core.corpus.CorpusData.*;
import edu.courseassist.core.react.*;
import edu.courseassist.core.react.ReactData.*;
import edu.courseassist.core.session.*;
import edu.courseassist.core.session.SessionData.*;
import edu.courseassist.model.CourseEmbeddings;
import edu.courseassist.storage.*;
import java.nio.file.*;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

/** Synthetic model/PDF ports around the real group-4 roots/controllers; no root guard fixture. */
public final class SessionTestEnvironment implements AutoCloseable {
    public final ExecutorService workers=Executors.newCachedThreadPool();
    public final H2Store store;public final RuntimeGate runtime=new RuntimeGate();public final CourseLibrary courses;
    public final SessionRepository repository;public final SessionCoordinator sessions;public final ApplicationCoordinator app;
    public final AtomicReference<Model> model=new AtomicReference<>((request,listener)->{finish(listener,"durable answer");return ()->{};});
    public final List<Request> requests=new CopyOnWriteArrayList<>();public final List<Throwable> observerErrors=new CopyOnWriteArrayList<>();
    public final AtomicInteger transportsClosed=new AtomicInteger();
    public final AtomicBoolean pauseGeneration=new AtomicBoolean();
    private final Queue<Runnable> pendingStarts=new ConcurrentLinkedQueue<>();
    public void resumeGeneration(){pauseGeneration.set(false);Runnable task;while((task=pendingStarts.poll())!=null)workers.execute(task);}
    public final List<String> queries=new CopyOnWriteArrayList<>();
    public final Map<CredentialUpdate.Slot,char[]> secrets=new EnumMap<>(CredentialUpdate.Slot.class);
    public final CredentialVault vault=new CredentialVault(){
        public synchronized char[] read(CredentialUpdate.Slot slot){return secrets.getOrDefault(slot,new char[0]).clone();}
        public synchronized void write(CredentialUpdate.Slot slot,char[] value){char[] old=secrets.put(slot,value.clone());if(old!=null)Arrays.fill(old,'\0');}
    };
    public ContextPlanner.Settings context=new ContextPlanner.Settings(180000,150000,1024,1,8000,Duration.ofSeconds(5),"synthetic-summary");
    public ErrorPolicy policy=ErrorPolicy.DEVELOPMENT;
    public SessionTestEnvironment(Path root) throws Exception {
        this(root, request -> { throw new UnsupportedOperationException("No PDF adapter in session test"); });
    }
    public SessionTestEnvironment(Path root, PdfReader reader) throws Exception {
        Files.createDirectories(root);store=new H2Store(root.resolve("db"));
        var space=new EmbeddingSpace("synthetic",URI.create("https://example.invalid"),"synthetic-embedding",3,"","","v1");
        CourseEmbeddings embeddings=new CourseEmbeddings(){public EmbeddingSpace space(){return space;}public float[] document(String s){return new float[]{1,0,0};}
            public float[] query(String s){queries.add(s);return new float[]{1,0,0};}};
        courses=new CourseLibrary(new CourseRepository(store),new ManagedPdfStore(root.resolve("pdf")),()->reader,workers);
        courses.initialize().get(10,TimeUnit.SECONDS);repository=new SessionRepository(store,runtime);
        app=new ApplicationCoordinator(new ApplicationRepository(store),runtime,courses,vault,(profiles,v)->new RuntimeResources((request,listener)->{
            requests.add(request);return model.get().subscribe(request,listener);},embeddings,new Settings(space,new Processing("synthetic",100,10),new IndexSettings(2,8,1.5f,1.2f)),
                new SearchSettings(2,.5f),new Limits(6,8,16000,32000,200000,1024,Duration.ofSeconds(10),1),context,ReactContext.utf8Budget(),policy,transportsClosed::incrementAndGet),workers,observerErrors::add);
        sessions=new SessionCoordinator(repository,runtime,courses,task->{if(pauseGeneration.get())pendingStarts.add(task);else workers.execute(task);},app::resources,observerErrors::add);app.sessions(sessions);
        sessions.initialize().toCompletableFuture().get(10,TimeUnit.SECONDS);app.initialize().toCompletableFuture().get(10,TimeUnit.SECONDS);
    }
    public void configureAndEnable() throws Exception {
        var profile=new ProviderProfile("test","synthetic",URI.create("https://example.invalid"),"synthetic",Optional.of(3));
        require(app.dispatch(new UpdateProviderConfig(appMeta(),app.snapshot().configRevision(),new ProviderConfiguration(profile,profile),Optional.empty()))
                .toCompletableFuture().get(10,TimeUnit.SECONDS).outcome()==Outcome.APPLIED,"configuration saved");
        require(app.dispatch(new EnablePlugin(appMeta())).toCompletableFuture().get(10,TimeUnit.SECONDS).outcome()==Outcome.APPLIED,"runtime enabled locally");
    }
    public ApplicationEventMeta appMeta(){return new ApplicationEventMeta(SessionData.id(),app.snapshot().revision());}
    public Binding open(String instance,String directory,boolean asNew) throws Exception {
        return sessions.open(new Location("C:/synthetic/"+directory,directory),instance,()->start->CompletableFuture.completedFuture(group(start)),asNew).toCompletableFuture().get(10,TimeUnit.SECONDS);
    }
    public String create(String instance) throws Exception {dispatch(new CreateSession(meta(instance,null),"session"));return sessions.snapshot(instance).activeSessionId().orElseThrow();}
    public EventMeta meta(String instance,String session){return new EventMeta(SessionData.id(),instance,Optional.ofNullable(session));}
    public CommandResult dispatch(ViewEvent event) throws Exception{return sessions.dispatch(event).toCompletableFuture().get(10,TimeUnit.SECONDS);}
    public SessionViewState view(String instance,String session){return sessions.snapshot(instance).sessions().stream().filter(s->s.sessionId().equals(session)).findFirst().orElseThrow();}
    public void text(String instance,String session,String text) throws Exception {DraftView d=view(instance,session).draft();require(dispatch(new UpdateDraftText(meta(instance,session),d.draftId(),d.revision(),text)).outcome()==Outcome.APPLIED,"draft saved");}
    public SubmitDraft submitEvent(String instance,String session){var d=view(instance,session).draft();return new SubmitDraft(meta(instance,session),d.draftId(),d.revision());}
    public void submit(String instance,String session) throws Exception {require(dispatch(submitEvent(instance,session)).outcome()==Outcome.APPLIED,"submission applied");}
    public void idle(String instance,String session) throws Exception {await(()->view(instance,session).activeOperation().isEmpty());sessions.refreshAll().toCompletableFuture().get(10,TimeUnit.SECONDS);}
    public static void await(BooleanSupplier predicate) throws Exception {long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(!predicate.getAsBoolean()){if(System.nanoTime()>deadline)throw new IllegalStateException("Condition timed out");Thread.sleep(10);}}
    public static CapturedCodeGroup group(SelectionStart start){return new CapturedCodeGroup(start,List.of(
            new AutomaticContext(ContextKind.METHOD,ContextStatus.CAPTURED,Optional.of("void x() { int x = 1; }"),Optional.of(start.selection()),Optional.empty()),
            new AutomaticContext(ContextKind.CLASS_STRUCTURE,ContextStatus.CAPTURED,Optional.of("class X { int field; }"),Optional.of(start.selection()),Optional.empty())));}
    public static SelectionStart selection(String capture,String instance){return new SelectionStart(capture,instance,new FileSnapshotRef("file:///X.java",7,"JAVA"),"int x = 1;",new SourceRange(1,1,1,11));}
    public static void finish(Listener listener,String text){listener.frame(new Body(text));listener.frame(new End("stop"));listener.complete();}
    public static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    public void closeForRestart() throws Exception { close(false); }
    @Override public void close() throws Exception { close(true); }
    private void close(boolean disable) throws Exception {
        if(disable&&runtime.snapshot().state()==WorkState.ON)app.dispatch(new DisablePlugin(appMeta())).toCompletableFuture().get(10,TimeUnit.SECONDS);
        sessions.close();app.shutdown().get(10,TimeUnit.SECONDS);courses.close();workers.shutdown();if(!workers.awaitTermination(15,TimeUnit.SECONDS))throw new IllegalStateException("Session workers did not stop");
        store.transaction(c->null).get(10,TimeUnit.SECONDS);store.close();secrets.values().forEach(v->Arrays.fill(v,'\0'));
    }
}
