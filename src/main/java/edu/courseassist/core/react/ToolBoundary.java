package edu.courseassist.core.react;

import edu.courseassist.core.corpus.CorpusData.Evidence;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import tools.jackson.databind.json.JsonMapper;
import static edu.courseassist.core.react.ReactData.*;

/** Fixed capability boundary. There is no arbitrary tool registry or model-callable apply. */
public final class ToolBoundary {
    public enum Capability { COURSE, TARGET, PROPOSAL }
    public record Result(String observation,List<Evidence> evidence) {
        public Result { Objects.requireNonNull(observation); evidence=List.copyOf(evidence); }
    }
    /** G2/G3 may install one scoped host adapter; absent capabilities are not advertised. */
    public interface EditTools {
        boolean supports(Capability capability);
        CompletableFuture<String> execute(Capability capability,Identity identity,String branchId,Call call,BooleanSupplier permitted);
    }
    private static final JsonMapper JSON=JsonMapper.builder()
            .enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private final CourseTools courses;
    private final Optional<EditTools> edits;
    public ToolBoundary(CourseTools courses,Optional<EditTools> edits){this.courses=Objects.requireNonNull(courses);this.edits=Objects.requireNonNull(edits);}
    public static Capability capability(String name) {
        return switch(name) {
            case "searchCourse","readCourseEvidence" -> Capability.COURSE;
            case "resolveEditTarget" -> Capability.TARGET;
            case "proposeCodeEdit" -> Capability.PROPOSAL;
            default -> throw new ProtocolFailure("Unknown tool capability");
        };
    }
    public List<ToolDefinition> definitions(boolean courseAvailable) {
        List<ToolDefinition> result=new ArrayList<>();
        if(courseAvailable)result.addAll(CourseTools.DEFINITIONS);
        for(Capability kind:List.of(Capability.TARGET,Capability.PROPOSAL))if(edits.filter(e->e.supports(kind)).isPresent()) {
            String name=kind==Capability.TARGET?"resolveEditTarget":"proposeCodeEdit";
            result.add(new ToolDefinition(name,"Use an existing host-bound F2 reference",ReactContext.json(Map.of(
                    "type","object","properties",Map.of("ref",Map.of("type","string")),"required",List.of("ref"),"additionalProperties",false))));
        }
        return List.copyOf(result);
    }
    public void validate(Call call,boolean courseAvailable,Set<String> suppliedEvidence) {
        Capability kind=capability(call.name());
        if(kind==Capability.COURSE) {
            if(!courseAvailable)throw new ProtocolFailure("Course capability unavailable");
            String argument=CourseTools.argument(call);
            if(call.name().equals("readCourseEvidence")&&!suppliedEvidence.contains(argument))throw new ProtocolFailure("Evidence ref was not supplied to this branch");
        } else {
            if(edits.filter(e->e.supports(kind)).isEmpty())throw new ProtocolFailure("F2 capability unavailable");
            var node=JSON.readTree(call.arguments());
            if(!node.isObject()||node.size()!=1||!node.hasNonNull("ref")||!node.get("ref").isString()||node.get("ref").asString().isBlank())
                throw new ProtocolFailure("Invalid complete F2 tool arguments");
        }
    }
    /** Original exceptions/cancellation propagate; only the owner may isolate COURSE failures. */
    public CompletableFuture<Result> execute(Identity identity,String branch,Call call,BooleanSupplier permitted) {
        Capability kind=capability(call.name());
        if(kind!=Capability.COURSE)
            return edits.orElseThrow(()->new ProtocolFailure("F2 capability unavailable"))
                    .execute(kind,identity,branch,call,permitted).thenApply(body->new Result(body,List.of()));
        String argument=CourseTools.argument(call);
        CompletableFuture<List<Evidence>> result=call.name().equals("searchCourse")?courses.search(argument,permitted):courses.read(argument).thenApply(List::of);
        return result.thenApply(evidence->new Result(ReactContext.json(evidence),evidence));
    }
}
