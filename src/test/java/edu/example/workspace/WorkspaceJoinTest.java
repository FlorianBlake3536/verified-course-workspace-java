package edu.example.workspace;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceJoinTest {
    @Test void domainProofControlsAdmission() {
        AtomicInteger created = new AtomicInteger();
        WorkspaceJoin.Directory directory = new WorkspaceJoin.Directory() {
            boolean verified = true;
            public Map<String, Object> verifyDomain(String domain) {
                verified = !verified;
                return Map.of("verified", verified);
            }
            public Map<String, Object> createUser(String email, String name, String key) {
                created.incrementAndGet();
                assertEquals("course:writing-101:ava@academy.example", key);
                return Map.of("id", "learner-1");
            }
        };
        WorkspaceJoin join = new WorkspaceJoin(directory);
        var input = new WorkspaceJoin.Request("academy.example", "ava@academy.example", "Ava", "writing-101");
        assertEquals("awaiting_domain_proof", join.enroll(input).get("state"));
        assertEquals("joined", join.enroll(input).get("state"));
        assertEquals(1, created.get());
        assertThrows(WorkspaceJoin.Rejected.class,
                () -> join.enroll(new WorkspaceJoin.Request("academy.example", "ava@other.example", "Ava", "writing-101")));
        assertEquals(1, created.get());
    }
}
