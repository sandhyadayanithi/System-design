package com.example.consistentHashing.controller;

import com.example.consistentHashing.model.MigrationReport;
import com.example.consistentHashing.service.StudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/migration")
public class MigrationController {

    @Autowired
    private StudentService studentService;

    // GET /api/migration/report - Returns latest migration report
    @GetMapping("/report")
    public ResponseEntity<MigrationReport> getLatestReport() {
        return ResponseEntity.ok(studentService.getLatestMigrationReport());
    }

    // GET /api/migration/history - Returns history of all migrations
    @GetMapping("/history")
    public ResponseEntity<List<MigrationReport>> getMigrationHistory() {
        return ResponseEntity.ok(studentService.getMigrationHistory());
    }
}
