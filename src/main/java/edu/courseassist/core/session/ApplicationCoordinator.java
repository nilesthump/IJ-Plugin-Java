package edu.courseassist.core.session;

import edu.courseassist.contract.*;
import edu.courseassist.contract.ApplicationContracts.*;
import edu.courseassist.contract.CredentialUpdate.*;
import edu.courseassist.contract.CommonContracts.*;
import edu.courseassist.core.RuntimeGate;
import edu.courseassist.core.corpus.CourseLibrary;
import edu.courseassist.storage.ApplicationRepository;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

/** Application commands own config/client lifetime; all projects share this one epoch. */
public final class ApplicationCoordinator implements ApplicationController,CredentialUpdateSink,AutoCloseable {
    @FunctionalInterface public interface Factory {RuntimeResources create(ProviderConfiguration profiles,CredentialVault vault) throws Exception;}
    private final ApplicationRepository repository;private final RuntimeGate runtime;private final CourseLibrary courses;
    private final CredentialVault vault;private final Factory factory;private final Executor workers;
    private final ExecutorService commands=Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("courseassist-application").factory());
    private final ScheduledExecutorService expiry=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("courseassist-secret-expiry").factory());
    private final List<Consumer<ApplicationViewState>> observers=new CopyOnWriteArrayList<>();
    private final Map<String,Staged> staged=new HashMap<>();private final Set<CourseLibrary.Job<?>> jobs=new HashSet<>();
    private final Map<String,Throwable> failures=new ConcurrentHashMap<>();
    private ApplicationRepository.Configuration config;private SessionCoordinator sessions;private volatile RuntimeResources active;
    private volatile ApplicationViewState view=new ApplicationViewState(0,WorkState.OFF,0,0,Optional.empty(),Optional.empty(),CorpusState.RESTORING,new CorpusBase(0,Optional.empty()),List.of(),false,List.of());
    private final List<Notice> notices=new ArrayList<>();private volatile boolean closed;private final Consumer<Throwable> observerFailures;
    private CompletableFuture<Void> shutdown;
    private boolean batchActive;
    private record Secret(Action action,char[] value) {void clear(){Arrays.fill(value,'\0');} @Override public String toString(){return "Secret[redacted]";}}
    private record Staged(Instant expires,Map<Slot,Secret> changes){void clear(){changes.values().forEach(Secret::clear);} @Override public String toString(){return "Staged[redacted]";}}
    public ApplicationCoordinator(ApplicationRepository repository,RuntimeGate runtime,CourseLibrary courses,CredentialVault vault,Factory factory,Executor workers,Consumer<Throwable> observerFailures){
        this.repository=repository;this.runtime=runtime;this.courses=courses;this.vault=vault;this.factory=factory;this.workers=workers;this.observerFailures=observerFailures;
        expiry.scheduleAtFixedRate(()->command(()->{expire();return null;}),30,30,TimeUnit.SECONDS);
    }
    public void sessions(SessionCoordinator sessions){if(this.sessions!=null)throw new IllegalStateException("Sessions already attached");this.sessions=sessions;}
    public CompletionStage<Void> initialize(){return command(()->{config=repository.initialize();courses.initialize().join();
        if(repository.enabled())try{startRuntime();}catch(Exception | LinkageError failure){
            String diagnostic=SessionData.id();failures.put(diagnostic,failure);
            notices.add(notice("RESTORE_ENABLE_FAILED","恢复启用失败："+edu.courseassist.model.ProviderFailure.message(failure)+"（诊断 "+diagnostic+"）"));
            java.util.logging.Logger.getLogger(ApplicationCoordinator.class.getName()).warning("F0 restore diagnostic "+diagnostic+"\n"+edu.courseassist.model.ProviderFailure.safeDiagnostic(failure));
        }
        publish();return null;});}
    public RuntimeResources resources(){RuntimeResources r=active;if(r==null||runtime.snapshot().state()!=WorkState.ON)throw new SessionData.Rejected(Rejection.PLUGIN_OFF);return r;}
    public ApplicationViewState snapshot(){return view;}
    public Optional<Throwable> failure(String operation){return Optional.ofNullable(failures.get(operation));}
    @Override public CompletionStage<String> stage(CredentialUpdate update){
        // Copy synchronously: caller may clear its carrier as soon as this stage completes.
        Map<Slot,Secret> copy=new EnumMap<>(Slot.class);
        try{for(Change change:update.changes())copy.put(change.slot(),new Secret(change.action(),change.copyValue()));}
        catch(RuntimeException failure){copy.values().forEach(Secret::clear);return CompletableFuture.failedFuture(failure);}
        var result=command(()->{expire();if(runtime.snapshot().state()!=WorkState.OFF){copy.values().forEach(Secret::clear);throw new SessionData.Rejected(Rejection.CONFIG_REQUIRES_OFF);}
            String ref=SessionData.id();staged.put(ref,new Staged(Instant.now().plusSeconds(120),copy));return ref;});
        result.whenComplete((r,e)->{if(e!=null)copy.values().forEach(Secret::clear);});return result;
    }
    private void expire(){var iterator=staged.values().iterator();while(iterator.hasNext()){Staged s=iterator.next();if(!s.expires().isAfter(Instant.now())){s.clear();iterator.remove();}}}
    @Override public CompletionStage<CommandResult> dispatch(ApplicationEvent event){return dispatch(event,-1);}
    private CompletionStage<CommandResult> dispatch(ApplicationEvent event,long continuationEpoch){return command(()->{
        expire();Staged secrets=event instanceof UpdateProviderConfig e?e.credentialUpdateRef().map(staged::remove).orElse(null):null;
        try{
            if(config==null)throw new IllegalStateException("Application not initialized");
            if(event.meta().expectedAppStateRevision()!=view.revision())return reject(event,Rejection.STALE_REVISION);
            if(event instanceof UpdateProviderConfig e){
                if(runtime.snapshot().state()!=WorkState.OFF)return reject(event,Rejection.CONFIG_REQUIRES_OFF);
                if(e.expectedConfigRevision()!=config.revision())return reject(event,Rejection.STALE_REVISION);
                if(e.credentialUpdateRef().isPresent()&&secrets==null)return reject(event,Rejection.INVALID_INPUT);
                // Cross-store update: preserve prior credentials for rollback; failure remains visible.
                Map<Slot,char[]> previous=new EnumMap<>(Slot.class);
                try{
                    if(secrets!=null)for(var entry:secrets.changes().entrySet()){previous.put(entry.getKey(),vault.read(entry.getKey()));vault.write(entry.getKey(),entry.getValue().value());}
                    config=repository.save(config.revision(),e.configuration());
                }catch(Exception failure){for(var entry:previous.entrySet())try{vault.write(entry.getKey(),entry.getValue());}catch(RuntimeException rollback){failure.addSuppressed(rollback);}throw failure;}
                finally{previous.values().forEach(value->Arrays.fill(value,'\0'));}
            }else if(event instanceof EnablePlugin){
                if(runtime.snapshot().state()!=WorkState.OFF)return reject(event,Rejection.BUSY);
                if(config.profiles().isEmpty())return reject(event,Rejection.INVALID_INPUT);
                startRuntime();
                try{repository.enabled(true);}catch(RuntimeException failure){RuntimeResources retiring=active;active=null;long stop=runtime.beginStop();runtime.finishStop(stop);try{retiring.close();}catch(Exception close){failure.addSuppressed(close);}throw failure;}
            }else if(event instanceof DisablePlugin){
                if(runtime.snapshot().state()==WorkState.OFF)return reject(event,Rejection.PLUGIN_OFF);
                repository.enabled(false);batchActive=false;
                long epoch=runtime.beginStop();publish();jobs.forEach(CourseLibrary.Job::cancel);
                sessions.stopAll().toCompletableFuture().join();
                // Corpus tasks also have a local settled boundary; no remote physical-ack wait.
                for(var job:List.copyOf(jobs))try{job.cancelAndRecord();}catch(RuntimeException failure){failures.put(job.id(),edu.courseassist.core.react.ReactEngine.unwrap(failure));notices.add(notice("MATERIAL_STOP_FAILED","资料任务收尾失败，请检查资料状态"));}
                jobs.clear();RuntimeResources retiring=active;active=null;runtime.finishStop(epoch);
                if(retiring!=null)workers.execute(()->{try{retiring.close();}catch(Exception failure){observerFailures.accept(failure);}});
            }else{
                if(continuationEpoch>=0&&!runtime.isCurrent(continuationEpoch))return reject(event,Rejection.PLUGIN_OFF);
                if((batchActive&&continuationEpoch<0)||(event instanceof ImportCoursePdfs&&continuationEpoch<0&&!jobs.isEmpty()))return reject(event,Rejection.BUSY);
                RuntimeResources r=resources();long epoch=runtime.snapshot().epoch();CourseLibrary.Job<?> job;
                if(event instanceof ImportCoursePdfs e){job=courses.importPdf(e.selectedFiles().getFirst(),e.selectedFiles().getFirst().getFileName().toString(),e.expectedCorpus(),r.corpus(),r.embeddings(),()->runtime.isCurrent(epoch));batchActive=true;}
                else if(event instanceof ImportCoursePdf e)job=courses.importPdf(e.selectedFile(),e.selectedFile().getFileName().toString(),e.expectedCorpus(),r.corpus(),r.embeddings(),()->runtime.isCurrent(epoch));
                else if(event instanceof ReplaceCourseMaterial e)job=courses.replacePdf(e.selectedFile(),e.selectedFile().getFileName().toString(),e.expectedCorpus(),e.target(),r.corpus(),r.embeddings(),()->runtime.isCurrent(epoch));
                else if(event instanceof RemoveCourseMaterial e)job=courses.remove(e.expectedCorpus(),e.target(),()->runtime.isCurrent(epoch));
                else return reject(event,Rejection.NOT_SUPPORTED);
                jobs.add(job);
                job.completion().whenComplete((done,error)->command(()->{
                    if(error!=null&&!edu.courseassist.core.react.ReactEngine.isCancellation(edu.courseassist.core.react.ReactEngine.unwrap(error))){failures.put(job.id(),edu.courseassist.core.react.ReactEngine.unwrap(error));
                        java.util.logging.Logger.getLogger(ApplicationCoordinator.class.getName()).warning("F0 material diagnostic "+job.id()+"\n"+edu.courseassist.model.ProviderFailure.safeDiagnostic(error));
                        notices.add(notice("MATERIAL_FAILED",(event instanceof RemoveCourseMaterial ? "资料移除未全部完成，请刷新列表检查结果；文件清理可在排除文件占用并重启 IDEA 后继续：" : "资料操作失败，新版本未发布：") + edu.courseassist.model.ProviderFailure.message(error) + "（任务 " + job.id() + "）"));}publish();return null;}));
                job.settled().whenComplete((done,error)->command(()->{jobs.remove(job);if(error!=null){failures.put(job.id(),edu.courseassist.core.react.ReactEngine.unwrap(error));notices.add(notice("MATERIAL_SAVE_FAILED","资料任务状态保存失败"));}publish();
                    if(event instanceof ImportCoursePdfs batch){
                        if(error==null&&!job.completion().toCompletableFuture().isCompletedExceptionally()&&runtime.isCurrent(epoch)&&batch.selectedFiles().size()>1){
                            dispatch(new ImportCoursePdfs(new ApplicationEventMeta(SessionData.id(),view.revision()),batch.selectedFiles().subList(1,batch.selectedFiles().size()),view.corpusBase()),epoch)
                                .whenComplete((result,failure)->{if(failure!=null||result.outcome()!=Outcome.APPLIED)command(()->{if(runtime.isCurrent(epoch))batchActive=false;notices.add(notice("BATCH_STOPPED","批量导入已停止，请检查资料列表后重新选择剩余文件"));publish();return null;});});
                        }else {if(runtime.isCurrent(epoch))batchActive=false;if(batch.selectedFiles().size()>1){notices.add(notice("BATCH_STOPPED","批量导入已停止，剩余 "+(batch.selectedFiles().size()-1)+" 个文件未导入"));publish();}}
                    }
                    return null;}));
                publish();return new CommandResult(event.meta().commandId(),Outcome.APPLIED,view.revision(),Optional.empty(),Optional.of(job.id()));
            }
            publish();return new CommandResult(event.meta().commandId(),Outcome.APPLIED,view.revision(),Optional.empty(),Optional.empty());
        }catch(SessionData.Rejected rejection){return reject(event,rejection.reason);}
        catch(Exception | LinkageError failure){
            String diagnostic=event.meta().commandId();
            failures.put(diagnostic,edu.courseassist.core.react.ReactEngine.unwrap(failure));
            String action=event instanceof EnablePlugin?"启用失败：":"操作失败：";
            notices.add(notice("APPLICATION_FAILED",action+edu.courseassist.model.ProviderFailure.message(failure)+"（诊断 "+diagnostic+"）"));
            java.util.logging.Logger.getLogger(ApplicationCoordinator.class.getName()).warning(
                "F0 command diagnostic "+diagnostic+"\n"+edu.courseassist.model.ProviderFailure.safeDiagnostic(failure));
            publish();throw failure;
        }
        finally{if(secrets!=null)secrets.clear();}
    });}
    private void startRuntime() throws Exception {
        if(config.profiles().isEmpty())throw new IllegalStateException("Provider configuration missing");
        long epoch=runtime.beginStart();
        try{publish();active=factory.create(config.profiles().get(),vault);runtime.finishStart(epoch);}
        catch(Exception | LinkageError failure){long stop=runtime.beginStop();runtime.finishStop(stop);throw failure;}
    }
    private Notice notice(String code,String text){return new Notice(SessionData.id(),Severity.ERROR,code,text,Optional.empty(),Optional.empty());}
    private CommandResult reject(ApplicationEvent event,Rejection reason){return new CommandResult(event.meta().commandId(),Outcome.REJECTED,view.revision(),Optional.of(reason),Optional.empty());}
    private Optional<ProfileView> profile(boolean generation){return config.profiles().map(p->{Slot slot=generation?Slot.GENERATION:Slot.EMBEDDING;char[] value=vault.read(slot);
        try{return new ProfileView(generation?p.generation():p.embedding(),value.length==0?CredentialStatus.MISSING:CredentialStatus.CONFIGURED);}finally{Arrays.fill(value,'\0');}});}
    private void publish(){var corpus=courses.snapshot().join();var state=runtime.snapshot();
        view=new ApplicationViewState(view.revision()+1,state.state(),config.revision(),state.epoch(),profile(true),profile(false),CorpusState.valueOf(corpus.availability().name()),
                corpus.base(),corpus.materials(),sessions!=null&&sessions.hasUnsavedState(),List.copyOf(notices));
        for(var listener:observers)try{listener.accept(view);}catch(RuntimeException failure){observerFailures.accept(failure);}}
    @Override public ViewSubscription observe(Consumer<ApplicationViewState> listener){AtomicBoolean subscribed=new AtomicBoolean(true);observers.add(listener);
        command(()->{if(subscribed.get())listener.accept(view);return null;});return ()->{subscribed.set(false);observers.remove(listener);};}
    private <T> CompletableFuture<T> command(Callable<T> work){CompletableFuture<T> result=new CompletableFuture<>();
        try{commands.execute(()->{try{if(closed)throw new IllegalStateException("Application controller closed");result.complete(work.call());}catch(Throwable failure){result.completeExceptionally(failure);}});}
        catch(RuntimeException failure){result.completeExceptionally(failure);}return result;}
    public synchronized CompletableFuture<Void> shutdown(){
        if(shutdown!=null)return shutdown.copy();expiry.shutdown();
        shutdown=command(()->{staged.values().forEach(Staged::clear);staged.clear();observers.clear();
            RuntimeException outcome=null;
            for(var job:jobs)try{job.cancelAndRecord();}catch(RuntimeException failure){if(outcome==null)outcome=failure;else outcome.addSuppressed(failure);}
            jobs.clear();RuntimeResources retiring=active;active=null;closed=true;
            if(retiring!=null)try{workers.execute(()->{try{retiring.close();}catch(Exception failure){observerFailures.accept(failure);}});}
            catch(RuntimeException failure){if(outcome==null)outcome=failure;else outcome.addSuppressed(failure);}
            if(outcome!=null)throw outcome;return null;});
        shutdown.whenComplete((v,e)->commands.shutdown());return shutdown.copy();
    }
    @Override public void close(){shutdown();}
}
