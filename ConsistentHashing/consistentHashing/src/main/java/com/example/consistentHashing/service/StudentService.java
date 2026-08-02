package com.example.consistentHashing.service;

import com.example.consistentHashing.model.Student;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class StudentService {

    @Autowired
    private ConsistentHashService hashService;

    // We inject the ApplicationContext so we can dynamically fetch the right 
    // MongoTemplate bean (mongo1, mongo2, or mongo3) by its String name.
    @Autowired
    private ApplicationContext context; 

    public String saveStudent(Student student) {
        // Step 1: Ask the hash ring which node this student's ID belongs to
        String targetNode = hashService.getNode(student.getId());
        
        // Step 2: Grab the specific MongoTemplate for that node
        MongoTemplate mongoTemplate = (MongoTemplate) context.getBean(targetNode);
        
        // Step 3: Save the student record into that specific MongoDB instance
        mongoTemplate.save(student, "student"); 
        
        return "Student " + student.getName() + " saved successfully in node: " + targetNode;
    }

    public Student getStudent(String id) {
        // Step 1: Figure out which node SHOULD have this student
        String targetNode = hashService.getNode(id);
        
        // Step 2: Fetch the template and retrieve the student
        MongoTemplate mongoTemplate = (MongoTemplate) context.getBean(targetNode);
        return mongoTemplate.findById(id, Student.class, "student");
    }

    public String addNodeAndMigrate(String newNode) {
        hashService.addNode(newNode);
        migrateData();
        return "Node " + newNode + " added to the hash ring and data migrated.";
    }

    public String removeNodeAndMigrate(String nodeToRemove) {
        hashService.removeNode(nodeToRemove);
        migrateData();
        return "Node " + nodeToRemove + " removed from the hash ring and data migrated.";
    }

    private void migrateData() {
        // We check all possible nodes defined in our config
        String[] allNodes = {"mongo1", "mongo2", "mongo3", "mongo4"};
        
        for (String sourceNode : allNodes) {
            try {
                MongoTemplate sourceTemplate = (MongoTemplate) context.getBean(sourceNode);
                List<Student> students = sourceTemplate.findAll(Student.class, "student");
                
                for (Student student : students) {
                    // Check where the student SHOULD be according to the updated hash ring
                    String targetNode = hashService.getNode(student.getId());
                    
                    // If the student is in the wrong node, migrate them
                    if (!sourceNode.equals(targetNode)) {
                        MongoTemplate targetTemplate = (MongoTemplate) context.getBean(targetNode);
                        targetTemplate.save(student, "student"); // Copy to new node
                        sourceTemplate.remove(student, "student"); // Delete from old node
                        System.out.println("Migrated student " + student.getId() + " from " + sourceNode + " to " + targetNode);
                    }
                }
            } catch (Exception e) {
                // Ignore if a bean isn't active or fails
            }
        }
    }
}