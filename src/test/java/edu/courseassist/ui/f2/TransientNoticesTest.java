package edu.courseassist.ui.f2;

import edu.courseassist.contract.CommonContracts.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransientNoticesTest {
    private Notice notice(String id,String operation){return new Notice(id,Severity.WARNING,"EDIT_SOURCE_CHANGED",
            "文件已被更改",Optional.of("session"),Optional.of(operation));}
    @Test void deduplicatesProjectionIdsButAllowsNewActionAndResolvedFaultRecurrence() {
        List<List<Notice>> shown=new ArrayList<>();var popups=new TransientNotices(shown::add);
        popups.update(List.of(notice("a","open-1"),notice("b","open-1")));
        popups.update(List.of(notice("c","open-1")));assertEquals(1,shown.size());assertEquals(1,shown.getFirst().size());
        popups.update(List.of(notice("d","open-2")));assertEquals(2,shown.size());
        popups.update(List.of());popups.update(List.of(notice("e","open-2")));assertEquals(3,shown.size());
        popups.close();popups.update(List.of(notice("f","open-3")));assertEquals(3,shown.size());
    }
}
