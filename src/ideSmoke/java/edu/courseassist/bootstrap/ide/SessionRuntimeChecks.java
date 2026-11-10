package edu.courseassist.bootstrap.ide;

import edu.courseassist.bootstrap.ProjectLocations;
import edu.courseassist.contract.ChatContracts.*;
import edu.courseassist.core.session.SessionData;
import java.nio.file.*;
import java.util.UUID;
import static edu.courseassist.bootstrap.ide.DependencyChecks.require;

/** Real IDE/plugin classloader, real H2 roots/controllers; synthetic model remains explicit. */
final class SessionRuntimeChecks {
    static void run(Path scratch) throws Exception {
        Files.createDirectories(scratch);String session,project;
        Path owned=scratch.resolve(UUID.randomUUID().toString());
        try(var e=new SessionTestEnvironment(owned)) {
            e.configureAndEnable();project=e.open("runtime-a","runtime-a",false).projectId();session=e.create("runtime-a");
            e.text("runtime-a",session,"raw question ```\n");e.sessions.beginCapture(SessionTestEnvironment.selection("runtime-code","runtime-a"));
            e.sessions.captureCompletion("runtime-code").toCompletableFuture().get();e.sessions.refreshAll().toCompletableFuture().get();
            var submit=e.submitEvent("runtime-a",session);var original=e.dispatch(submit);e.idle("runtime-a",session);
            require(e.dispatch(submit).operationId().equals(original.operationId()),"Runtime dedup returns original operation");
            require(e.requests.size()==1,"Runtime exactly one model subscription");require(e.view("runtime-a",session).turns().getFirst().userMessage().parts().size()==2,"User sees only text and selected capture");
            require(e.repository.states(project,"runtime-a").getFirst().turns().getFirst().input().code().size()==3,"All original typed code remains durable in H2");
            require(e.requests.getFirst().messages().stream().anyMatch(m->m.content().contains("CLASS_STRUCTURE")&&m.content().contains("METHOD")),"Implicit context still reaches model");
            e.dispatch(new CloseSessionTab(e.meta("runtime-a",session)));e.dispatch(new OpenSessionTab(e.meta("runtime-a",session)));
            require(e.requests.size()==1,"Tab reopen performs no model work");e.text("runtime-a",session,"durable next draft");
        }
        try(var e=new SessionTestEnvironment(owned)) {
            require(e.open("runtime-reopen","runtime-a",false).projectId().equals(project),"Stable project restore");
            require(e.requests.isEmpty(),"Restore performs no network");require(e.view("runtime-reopen",session).draft().text().equals("durable next draft"),"Draft recovered");
            require(e.view("runtime-reopen",session).turns().getFirst().attempts().getFirst().status()==AttemptStatus.SUCCEEDED,"Committed answer recovered");
            e.dispatch(new DeleteSession(e.meta("runtime-reopen",session)));require(e.sessions.snapshot("runtime-reopen").sessions().isEmpty(),"Hard session deletion");
        }
        Path before=scratch.resolve("identity-before-"+SessionData.id());Files.createDirectory(before);var original=ProjectLocations.read(before);
        Path after=before.resolveSibling("identity-after-"+SessionData.id());Files.move(before,after);var moved=ProjectLocations.read(after);
        Path copy=before.resolveSibling("identity-copy-"+SessionData.id());Files.createDirectory(copy);var copied=ProjectLocations.read(copy);
        require(original.directoryKey().equals(moved.directoryKey()),"Native directory identity survives same-volume move");
        require(!original.directoryKey().equals(copied.directoryKey()),"Copy has independent directory identity");
    }
}
