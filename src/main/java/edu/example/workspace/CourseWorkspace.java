package edu.example.workspace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@SpringBootApplication
@RestController
public class CourseWorkspace {
    private final WorkspaceJoin join;

    public CourseWorkspace(WorkspaceJoin join) { this.join = join; }

    public static void main(String[] args) { SpringApplication.run(CourseWorkspace.class, args); }

    @PostMapping("/workspace/join")
    public Map<String, Object> join(@RequestBody WorkspaceJoin.Request request) { return join.enroll(request); }

    @ExceptionHandler(WorkspaceJoin.Rejected.class)
    public ResponseEntity<Map<String, String>> rejected(WorkspaceJoin.Rejected exception) {
        return ResponseEntity.status(422).body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(InfraiGateway.ApiError.class)
    public ResponseEntity<Map<String, String>> upstream(InfraiGateway.ApiError exception) {
        int status = exception.status >= 400 && exception.status < 500 ? exception.status : 502;
        return ResponseEntity.status(status).body(Map.of("error", exception.code, "message", exception.getMessage()));
    }
}
