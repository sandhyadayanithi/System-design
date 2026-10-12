package com.example.consistentHashing.controller;

import com.example.consistentHashing.model.MigrationReport;
import com.example.consistentHashing.model.Student;
import com.example.consistentHashing.model.StudentResponse;
import com.example.consistentHashing.service.StudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    @Autowired
    private StudentService studentService;

    // POST /api/students - Add a student
    @PostMapping
    public ResponseEntity<StudentResponse> addStudent(@RequestBody Student student) {
        StudentResponse response = studentService.saveStudent(student);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET /api/students/{id} - Fetch a student by ID and include allocated node and address
    @GetMapping("/{id}")
    public ResponseEntity<?> getStudent(@PathVariable String id) {
        StudentResponse response = studentService.getStudent(id);
        if (response == null || response.getStudent() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", HttpStatus.NOT_FOUND.value(),
                    "error", "Not Found",
                    "message", "Student not found with id: " + id
            ));
        }
        return ResponseEntity.ok(response);
    }

    // GET /api/students - List all students across active MongoDB nodes
    @GetMapping
    public ResponseEntity<List<StudentResponse>> getAllStudents() {
        return ResponseEntity.ok(studentService.getAllStudents());
    }

    // POST /api/students/node/{nodeName} - Add a database node and trigger verified migration
    @PostMapping("/node/{nodeName}")
    public ResponseEntity<MigrationReport> addNode(@PathVariable String nodeName) {
        MigrationReport report = studentService.addNodeAndMigrate(nodeName);
        return ResponseEntity.ok(report);
    }

    // DELETE /api/students/node/{nodeName} - Remove a database node and trigger verified migration
    @DeleteMapping("/node/{nodeName}")
    public ResponseEntity<MigrationReport> removeNode(@PathVariable String nodeName) {
        MigrationReport report = studentService.removeNodeAndMigrate(nodeName);
        return ResponseEntity.ok(report);
    }

    // GET /api/students/migration/report - Retrieve latest migration report
    @GetMapping("/migration/report")
    public ResponseEntity<MigrationReport> getMigrationReport() {
        return ResponseEntity.ok(studentService.getLatestMigrationReport());
    }

    // GET /api/students/ring - View consistent hash ring and cluster topology
    @GetMapping("/ring")
    public ResponseEntity<?> getRingInfo() {
        return ResponseEntity.ok(studentService.getRingInfo());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", "Bad Request",
                "message", e.getMessage()
        ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "status", HttpStatus.SERVICE_UNAVAILABLE.value(),
                "error", "Service Unavailable",
                "message", e.getMessage()
        ));
    }
}