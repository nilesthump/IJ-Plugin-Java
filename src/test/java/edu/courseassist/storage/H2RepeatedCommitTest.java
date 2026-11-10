package edu.courseassist.storage;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class H2RepeatedCommitTest {
    @TempDir Path directory;
    @Test void repeatedDraftSizedClobWritesReopenWithoutCloseFailure() throws Exception {
        try (var store = new H2Store(directory)) {
            store.transaction(c -> { try(var s=c.createStatement()) { s.execute("CREATE TABLE feedback_probe(id INT PRIMARY KEY, body CLOB)"); } return null; }).get(10,TimeUnit.SECONDS);
            for(int i=0;i<120;i++) {
                int value=i;
                store.transaction(c -> {
                    try(var s=c.prepareStatement("MERGE INTO feedback_probe KEY(id) VALUES (?,?)")) {
                        s.setInt(1,value%7);s.setString(2,("draft-"+value+" abcdefghijklmnopqrstuvwxyz ").repeat(150));s.executeUpdate();
                    }
                    return null;
                }).get(10,TimeUnit.SECONDS);
            }
            assertEquals(7,store.transaction(c -> {try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM feedback_probe")){r.next();return r.getInt(1);}}).get(10,TimeUnit.SECONDS));
        }
    }
}
