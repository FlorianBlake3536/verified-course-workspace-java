package edu.example.workspace;

import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class WorkspaceJoin {
    public record Request(String companyDomain, String email, String name, String courseId) {}
    public interface Directory {
        Map<String, Object> verifyDomain(String domain);
        Map<String, Object> createUser(String email, String name, String idempotencyKey);
    }
    public static class Rejected extends RuntimeException {
        public Rejected(String message) { super(message); }
    }

    private final Directory directory;
    public WorkspaceJoin(Directory directory) { this.directory = directory; }

    public Map<String, Object> enroll(Request request) {
        if (request == null || request.companyDomain() == null || request.email() == null
                || request.name() == null || request.courseId() == null
                || request.companyDomain().isBlank() || request.email().isBlank()
                || request.name().isBlank() || request.courseId().isBlank())
            throw new Rejected("Company domain, email, name and course ID are required");
        String domain = request.companyDomain().strip().toLowerCase(java.util.Locale.ROOT);
        String email = request.email().strip().toLowerCase(java.util.Locale.ROOT);
        if (!email.endsWith("@" + domain) || email.indexOf('@') != email.lastIndexOf('@'))
            throw new Rejected("Use your company email address");

        // Verification is read-only; registration has no matching cleanup capability.
        Map<String, Object> proof = directory.verifyDomain(domain);
        if (!Boolean.TRUE.equals(proof.get("verified")))
            return Map.of("state", "awaiting_domain_proof", "domain", domain);

        Map<String, Object> user = directory.createUser(email, request.name(),
                "course:" + request.courseId() + ":" + email);
        return Map.of("state", "joined", "course_id", request.courseId(),
                "domain", domain, "user", user);
    }
}
