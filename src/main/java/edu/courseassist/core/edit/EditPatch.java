package edu.courseassist.core.edit;

import edu.courseassist.contract.ChatContracts.FailureCode;
import edu.courseassist.contract.ChatContracts.FailurePhase;
import edu.courseassist.contract.EditContracts.*;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;
import static edu.courseassist.core.react.ReactData.*;

/** Exact structured replacements, never fuzzy patches or post-generation formatting. */
public final class EditPatch {
    private EditPatch() {}
    public static final JsonMapper JSON=JsonMapper.builder()
            .enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    public static String apply(EditSnapshot snapshot,String body) {
        tools.jackson.databind.JsonNode root;
        try { root=JSON.readTree(body); }
        catch(RuntimeException malformed) { throw new ProtocolFailure("Invalid candidate JSON",FailureCode.CANDIDATE_FORMAT,FailurePhase.PATCH_VALIDATION); }
        if(root==null||!root.isObject()||root.size()!=1||!root.has("changes")||!root.get("changes").isArray()||root.get("changes").size()>64)
            throw new ProtocolFailure("Invalid candidate structure",FailureCode.CANDIDATE_FORMAT,FailurePhase.PATCH_VALIDATION);
        String before=snapshot.before();StringBuilder after=new StringBuilder();int cursor=0;boolean first=true;
        for(var change:root.get("changes")) {
            if(!change.isObject()||change.size()!=4||!change.has("start")||!change.has("end")||!change.has("before")||!change.has("after")
                    ||!change.get("start").isIntegralNumber()||!change.get("end").isIntegralNumber()||!change.get("start").canConvertToInt()||!change.get("end").canConvertToInt()
                    ||!change.get("before").isString()||!change.get("after").isString())
                throw new ProtocolFailure("Invalid replacement fields",FailureCode.CANDIDATE_FORMAT,FailurePhase.PATCH_VALIDATION);
            int start=change.get("start").asInt(),end=change.get("end").asInt();
            if(start<0||end<start||end>before.length())
                throw new ProtocolFailure("Replacement start/end out of bounds",FailureCode.CANDIDATE_RANGE_BOUNDS,FailurePhase.PATCH_VALIDATION);
            if(start<cursor||(!first&&start==cursor&&start==end))
                throw new ProtocolFailure("Replacement overlaps or is out of order",FailureCode.CANDIDATE_RANGE_OVERLAP,FailurePhase.PATCH_VALIDATION);
            if(snapshot.allowedRanges().stream().noneMatch(r->start>=r.start()&&end<=r.end()))
                throw new ProtocolFailure("Replacement outside allowed range",FailureCode.CANDIDATE_RANGE_OUTSIDE_ALLOWED,FailurePhase.PATCH_VALIDATION);
            if(!before.substring(start,end).equals(change.get("before").asString()))
                throw new ProtocolFailure("Replacement before mismatch",FailureCode.CANDIDATE_BEFORE_MISMATCH,FailurePhase.PATCH_VALIDATION);
            after.append(before,cursor,start).append(change.get("after").asString());cursor=end;first=false;
            if(after.length()>edu.courseassist.contract.EditContracts.MAX_DOCUMENT_CHARS)
                throw new Limited("Candidate size limit",FailureCode.RESULT_SIZE_LIMIT,FailurePhase.PATCH_VALIDATION);
        }
        after.append(before,cursor,before.length());
        if(after.length()>edu.courseassist.contract.EditContracts.MAX_DOCUMENT_CHARS)
            throw new Limited("Candidate size limit",FailureCode.RESULT_SIZE_LIMIT,FailurePhase.PATCH_VALIDATION);
        return after.toString();
    }
}
