package com.example.consistentHashing.controller;

import com.example.consistentHashing.model.Student;
import com.example.consistentHashing.service.StudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    @Autowired
    private StudentService studentService;

    // POST request to add a student
    @PostMapping
    public String addStudent(@RequestBody Student student) {
        return studentService.saveStudent(student);
    }

    // GET request to fetch a student by ID
    @GetMapping("/{id}")
    public Student getStudent(@PathVariable String id) {
        return studentService.getStudent(id);
    }

    // POST request to add a new database node
    @PostMapping("/node/{nodeName}")
    public String addNode(@PathVariable String nodeName) {
        return studentService.addNodeAndMigrate(nodeName);
    }

    // DELETE request to remove a database node
    @DeleteMapping("/node/{nodeName}")
    public String removeNode(@PathVariable String nodeName) {
        return studentService.removeNodeAndMigrate(nodeName);
    }
}