package com.example.consistentHashing.controller;

import com.example.consistentHashing.model.MigrationReport;
import com.example.consistentHashing.model.Student;
import com.example.consistentHashing.model.StudentResponse;
import com.example.consistentHashing.service.StudentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class StudentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private StudentService studentService;

    @InjectMocks
    private StudentController studentController;

    private MockMvc migrationMockMvc;

    @InjectMocks
    private MigrationController migrationController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(studentController).build();
        migrationMockMvc = MockMvcBuilders.standaloneSetup(migrationController).build();
    }

    @Test
    void testGetStudentFound() throws Exception {
        Student student = new Student("101", "Alice", "CSE");
        StudentResponse response = StudentResponse.builder()
                .student(student)
                .allocatedNode("mongo1")
                .nodeAddress("localhost:27017")
                .build();

        when(studentService.getStudent("101")).thenReturn(response);

        mockMvc.perform(get("/api/students/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.student.id").value("101"))
                .andExpect(jsonPath("$.student.name").value("Alice"))
                .andExpect(jsonPath("$.allocatedNode").value("mongo1"))
                .andExpect(jsonPath("$.nodeAddress").value("localhost:27017"));
    }

    @Test
    void testGetStudentNotFoundReturns404() throws Exception {
        when(studentService.getStudent("99999")).thenReturn(null);

        mockMvc.perform(get("/api/students/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void testAddStudentSuccess() throws Exception {
        Student student = new Student("102", "Bob", "ECE");
        StudentResponse response = StudentResponse.builder()
                .message("Student Bob saved successfully in node: mongo2")
                .student(student)
                .allocatedNode("mongo2")
                .nodeAddress("localhost:27018")
                .build();

        when(studentService.saveStudent(any(Student.class))).thenReturn(response);

        mockMvc.perform(post("/api/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"102\",\"name\":\"Bob\",\"department\":\"ECE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.student.id").value("102"))
                .andExpect(jsonPath("$.allocatedNode").value("mongo2"))
                .andExpect(jsonPath("$.nodeAddress").value("localhost:27018"));
    }

    @Test
    void testAddNodeTriggersMigrationReport() throws Exception {
        MigrationReport report = MigrationReport.builder()
                .action("ADD_NODE")
                .targetNode("mongo4")
                .targetNodeAddress("localhost:27020")
                .status("SUCCESS")
                .totalExamined(2)
                .migratedCount(1)
                .unchangedCount(1)
                .failedCount(0)
                .activeNodes(List.of("mongo1", "mongo2", "mongo3", "mongo4"))
                .build();

        when(studentService.addNodeAndMigrate("mongo4")).thenReturn(report);

        mockMvc.perform(post("/api/students/node/mongo4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("ADD_NODE"))
                .andExpect(jsonPath("$.targetNode").value("mongo4"))
                .andExpect(jsonPath("$.totalExamined").value(2))
                .andExpect(jsonPath("$.migratedCount").value(1))
                .andExpect(jsonPath("$.unchangedCount").value(1))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void testGetMigrationReportEndpoint() throws Exception {
        MigrationReport report = MigrationReport.builder()
                .action("ADD_NODE")
                .targetNode("mongo4")
                .status("SUCCESS")
                .totalExamined(5)
                .migratedCount(2)
                .unchangedCount(3)
                .build();

        when(studentService.getLatestMigrationReport()).thenReturn(report);

        migrationMockMvc.perform(get("/api/migration/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetNode").value("mongo4"))
                .andExpect(jsonPath("$.migratedCount").value(2));
    }
}
