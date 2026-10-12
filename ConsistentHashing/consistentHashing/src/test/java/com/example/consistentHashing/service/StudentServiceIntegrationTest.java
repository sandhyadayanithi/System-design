package com.example.consistentHashing.service;

import com.example.consistentHashing.model.MigrationItem;
import com.example.consistentHashing.model.MigrationReport;
import com.example.consistentHashing.model.Student;
import com.example.consistentHashing.model.StudentResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class StudentServiceIntegrationTest {

    @Autowired
    private StudentService studentService;

    @Autowired
    private ConsistentHashService hashService;

    @AfterEach
    void tearDown() {
        // Reset hash ring back to initial 3 nodes if mongo4 was added
        if (hashService.hasNode("mongo4")) {
            studentService.removeNodeAndMigrate("mongo4");
        }
    }

    @Test
    void testSaveAndGetStudentWithAllocatedNode() {
        Student student = new Student("TEST_901", "Zara", "AI");
        StudentResponse saveResponse = studentService.saveStudent(student);

        assertNotNull(saveResponse);
        assertNotNull(saveResponse.getAllocatedNode());
        assertNotNull(saveResponse.getNodeAddress());
        assertTrue(saveResponse.getAllocatedNode().startsWith("mongo"));
        assertTrue(saveResponse.getNodeAddress().startsWith("localhost:"));

        // Retrieve student
        StudentResponse getResponse = studentService.getStudent("TEST_901");
        assertNotNull(getResponse);
        assertEquals("TEST_901", getResponse.getStudent().getId());
        assertEquals("Zara", getResponse.getStudent().getName());
        assertEquals(saveResponse.getAllocatedNode(), getResponse.getAllocatedNode());
        assertEquals(saveResponse.getNodeAddress(), getResponse.getNodeAddress());

        // Verify physical presence in that specific MongoDB node
        MongoTemplate template = studentService.getTemplateForNode(getResponse.getAllocatedNode());
        Student physical = template.findById("TEST_901", Student.class, "student");
        assertNotNull(physical);
        assertEquals("Zara", physical.getName());

        // Clean up test document
        template.remove(physical, "student");
    }

    @Test
    void testGetNonExistentStudentReturnsNull() {
        StudentResponse response = studentService.getStudent("NON_EXISTENT_ID_99999");
        assertNull(response);
    }

    @Test
    void testAddNodeMigrationWorkflow() {
        // Insert a test student batch
        Student s1 = new Student("TEST_MIG_1", "User One", "CS");
        Student s2 = new Student("TEST_MIG_2", "User Two", "IT");
        Student s3 = new Student("TEST_MIG_3", "User Three", "EE");

        studentService.saveStudent(s1);
        studentService.saveStudent(s2);
        studentService.saveStudent(s3);

        String initialNode1 = hashService.getNode(s1.getId());
        String initialNode2 = hashService.getNode(s2.getId());
        String initialNode3 = hashService.getNode(s3.getId());

        // Add node mongo4 and execute migration
        MigrationReport report = studentService.addNodeAndMigrate("mongo4");

        assertNotNull(report);
        assertEquals("ADD_NODE", report.getAction());
        assertEquals("mongo4", report.getTargetNode());
        assertEquals("SUCCESS", report.getStatus());
        assertTrue(report.getTotalExamined() >= 3);
        assertEquals(report.getTotalExamined(), report.getMigratedCount() + report.getUnchangedCount() + report.getFailedCount());
        assertEquals(0, report.getFailedCount());

        // Verify each test student's state after migration
        for (Student s : List.of(s1, s2, s3)) {
            String currentNode = hashService.getNode(s.getId());
            StudentResponse current = studentService.getStudent(s.getId());
            assertNotNull(current, "Student " + s.getId() + " should still be accessible");
            assertEquals(currentNode, current.getAllocatedNode());

            // Verify document physically exists on the current allocated node
            MongoTemplate currentTemplate = studentService.getTemplateForNode(currentNode);
            assertNotNull(currentTemplate.findById(s.getId(), Student.class, "student"));

            // If the student was migrated, verify they are no longer on the previous node
            for (MigrationItem item : report.getMigratedStudents()) {
                if (item.getStudentId().equals(s.getId())) {
                    assertEquals("SUCCESS", item.getStatus());
                    MongoTemplate oldTemplate = studentService.getTemplateForNode(item.getPreviousNode());
                    assertNull(oldTemplate.findById(s.getId(), Student.class, "student"),
                            "Migrated student must be removed from old node " + item.getPreviousNode());
                }
            }

            // Cleanup test record
            currentTemplate.remove(s, "student");
        }
    }
}
