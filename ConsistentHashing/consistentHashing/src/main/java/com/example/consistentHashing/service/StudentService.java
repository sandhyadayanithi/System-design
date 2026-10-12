package com.example.consistentHashing.service;

import com.example.consistentHashing.model.MigrationItem;
import com.example.consistentHashing.model.MigrationReport;
import com.example.consistentHashing.model.Student;
import com.example.consistentHashing.model.StudentResponse;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class StudentService {

    @Autowired
    private ConsistentHashService hashService;

    @Autowired
    private ApplicationContext context;

    private static final List<String> SUPPORTED_NODES = List.of("mongo1", "mongo2", "mongo3", "mongo4");

    private volatile MigrationReport latestMigrationReport;
    private final List<MigrationReport> migrationHistory = new CopyOnWriteArrayList<>();

    public StudentResponse saveStudent(Student student) {
        if (student == null || student.getId() == null || student.getId().isBlank()) {
            throw new IllegalArgumentException("Student ID cannot be null or empty.");
        }

        // Step 1: Ask the hash ring which node this student's ID belongs to
        String targetNode = hashService.getNode(student.getId());

        // Step 2: Verify connectivity to the designated node
        checkNodeConnectivity(targetNode);

        // Step 3: Grab MongoTemplate for that node and save
        MongoTemplate mongoTemplate = getTemplateForNode(targetNode);
        mongoTemplate.save(student, "student");

        String nodeAddress = hashService.getNodeAddress(targetNode);
        String message = "Student " + student.getName() + " saved successfully in node: " + targetNode;

        return StudentResponse.builder()
                .message(message)
                .student(student)
                .allocatedNode(targetNode)
                .nodeAddress(nodeAddress)
                .build();
    }

    public StudentResponse getStudent(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Student ID cannot be null or empty.");
        }

        // Step 1: Figure out which node SHOULD have this student
        String targetNode = hashService.getNode(id);

        // Step 2: Verify connectivity
        checkNodeConnectivity(targetNode);

        // Step 3: Query target node
        MongoTemplate mongoTemplate = getTemplateForNode(targetNode);
        Student student = mongoTemplate.findById(id, Student.class, "student");

        if (student == null) {
            return null; // Return null so controller can return 404
        }

        return StudentResponse.builder()
                .student(student)
                .allocatedNode(targetNode)
                .nodeAddress(hashService.getNodeAddress(targetNode))
                .build();
    }

    public List<StudentResponse> getAllStudents() {
        Set<String> activeNodes = hashService.getActiveNodes();
        Map<String, StudentResponse> studentMap = new LinkedHashMap<>();

        for (String node : activeNodes) {
            if (!isNodeReachable(node)) {
                continue;
            }
            MongoTemplate template = getTemplateForNode(node);
            List<Student> students = template.findAll(Student.class, "student");
            for (Student student : students) {
                String responsibleNode = hashService.getNode(student.getId());
                // If not seen or if current node matches the hash ring, record it
                if (!studentMap.containsKey(student.getId()) || node.equals(responsibleNode)) {
                    studentMap.put(student.getId(), StudentResponse.builder()
                            .student(student)
                            .allocatedNode(node)
                            .nodeAddress(hashService.getNodeAddress(node))
                            .build());
                }
            }
        }
        return new ArrayList<>(studentMap.values());
    }

    public synchronized MigrationReport addNodeAndMigrate(String newNode) {
        String normalized = hashService.normalizeNodeName(newNode);

        if (!SUPPORTED_NODES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported node: '" + newNode + "'. Configured nodes are: " + SUPPORTED_NODES);
        }

        if (hashService.hasNode(normalized)) {
            throw new IllegalStateException("Node '" + normalized + "' is already active in the hash ring.");
        }

        // Verify target node connectivity before ring modification
        checkNodeConnectivity(normalized);

        // Verify all currently active nodes connectivity
        for (String activeNode : hashService.getActiveNodes()) {
            checkNodeConnectivity(activeNode);
        }

        // Step 1: Capture previous node assignments of existing students
        List<StudentRecord> existingRecords = new ArrayList<>();
        for (String sourceNode : hashService.getActiveNodes()) {
            MongoTemplate srcTemplate = getTemplateForNode(sourceNode);
            List<Student> students = srcTemplate.findAll(Student.class, "student");
            for (Student s : students) {
                existingRecords.add(new StudentRecord(s, sourceNode));
            }
        }

        // Step 2: Add the new node to the ring
        hashService.addNode(normalized);

        // Step 3-6: Recalculate, physically migrate, verify destination read, then delete from source
        MigrationReport report = performMigration("ADD_NODE", normalized, existingRecords);

        this.latestMigrationReport = report;
        this.migrationHistory.add(report);
        return report;
    }

    public synchronized MigrationReport removeNodeAndMigrate(String nodeToRemove) {
        String normalized = hashService.normalizeNodeName(nodeToRemove);

        if (!hashService.hasNode(normalized)) {
            throw new IllegalStateException("Node '" + normalized + "' is not active in the hash ring.");
        }

        if (hashService.getActiveNodes().size() <= 1) {
            throw new IllegalStateException("Cannot remove node '" + normalized + "'. At least one node must remain in the hash ring.");
        }

        // Verify active nodes connectivity
        for (String activeNode : hashService.getActiveNodes()) {
            checkNodeConnectivity(activeNode);
        }

        // Step 1: Capture previous node assignments
        List<StudentRecord> existingRecords = new ArrayList<>();
        for (String sourceNode : hashService.getActiveNodes()) {
            MongoTemplate srcTemplate = getTemplateForNode(sourceNode);
            List<Student> students = srcTemplate.findAll(Student.class, "student");
            for (Student s : students) {
                existingRecords.add(new StudentRecord(s, sourceNode));
            }
        }

        // Step 2: Remove node from ring
        hashService.removeNode(normalized);

        // Step 3-6: Recalculate, physically migrate, verify destination read, then delete from source
        MigrationReport report = performMigration("REMOVE_NODE", normalized, existingRecords);

        this.latestMigrationReport = report;
        this.migrationHistory.add(report);
        return report;
    }

    private MigrationReport performMigration(String action, String targetNode, List<StudentRecord> existingRecords) {
        int totalExamined = existingRecords.size();
        int migratedCount = 0;
        int unchangedCount = 0;
        int failedCount = 0;
        List<MigrationItem> items = new ArrayList<>();

        for (StudentRecord record : existingRecords) {
            Student student = record.student;
            String sourceNode = record.sourceNode;
            String destinationNode = hashService.getNode(student.getId());

            if (sourceNode.equals(destinationNode)) {
                unchangedCount++;
                continue;
            }

            MigrationItem item = MigrationItem.builder()
                    .studentId(student.getId())
                    .studentName(student.getName())
                    .previousNode(sourceNode)
                    .previousNodeAddress(hashService.getNodeAddress(sourceNode))
                    .destinationNode(destinationNode)
                    .destinationNodeAddress(hashService.getNodeAddress(destinationNode))
                    .build();

            try {
                MongoTemplate destTemplate = getTemplateForNode(destinationNode);
                MongoTemplate srcTemplate = getTemplateForNode(sourceNode);

                // Step 5: Physically write to destination
                destTemplate.save(student, "student");

                // Step 6: Verify write succeeded before deleting old copy
                Student verified = destTemplate.findById(student.getId(), Student.class, "student");
                if (verified == null || !student.getId().equals(verified.getId())) {
                    throw new IllegalStateException("Destination verification read failed on node: " + destinationNode);
                }

                // Step 6b: Successfully verified, remove old copy from source
                srcTemplate.remove(student, "student");

                item.setStatus("SUCCESS");
                migratedCount++;
                System.out.println("Verified migration of student " + student.getId() + " from " + sourceNode + " to " + destinationNode);
            } catch (Exception e) {
                item.setStatus("FAILED");
                item.setFailureDetails(e.getMessage());
                failedCount++;
                System.err.println("Migration failed for student " + student.getId() + ": " + e.getMessage());
            }

            items.add(item);
        }

        String status = (failedCount == 0) ? "SUCCESS" : (migratedCount > 0 ? "PARTIAL_FAILURE" : "FAILED");
        String message = String.format("Migration completed (%s %s). Total examined: %d, Migrated: %d, Unchanged: %d, Failed: %d",
                action, targetNode, totalExamined, migratedCount, unchangedCount, failedCount);

        return MigrationReport.builder()
                .action(action)
                .targetNode(targetNode)
                .targetNodeAddress(hashService.getNodeAddress(targetNode))
                .status(status)
                .message(message)
                .timestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .totalExamined(totalExamined)
                .migratedCount(migratedCount)
                .unchangedCount(unchangedCount)
                .failedCount(failedCount)
                .activeNodes(new ArrayList<>(hashService.getActiveNodes()))
                .migratedStudents(items)
                .build();
    }

    public MigrationReport getLatestMigrationReport() {
        if (latestMigrationReport != null) {
            return latestMigrationReport;
        }

        return MigrationReport.builder()
                .action("NONE")
                .status("NO_MIGRATIONS_RUN")
                .message("No migration has been performed yet. Add or remove a node to trigger migration.")
                .timestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .totalExamined(0)
                .migratedCount(0)
                .unchangedCount(0)
                .failedCount(0)
                .activeNodes(new ArrayList<>(hashService.getActiveNodes()))
                .migratedStudents(Collections.emptyList())
                .build();
    }

    public List<MigrationReport> getMigrationHistory() {
        return new ArrayList<>(migrationHistory);
    }

    public Map<String, Object> getRingInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("activeNodes", hashService.getActiveNodes());
        info.put("virtualNodesPerNode", hashService.getVirtualNodesCount());
        info.put("nodeAddresses", hashService.getAllNodeAddresses());

        Map<String, String> health = new LinkedHashMap<>();
        for (String node : SUPPORTED_NODES) {
            health.put(node, isNodeReachable(node) ? "UP" : "DOWN");
        }
        info.put("nodeHealth", health);
        info.put("ringSize", hashService.getRingCopy().size());
        return info;
    }

    public MongoTemplate getTemplateForNode(String nodeName) {
        String normalized = hashService.normalizeNodeName(nodeName);
        try {
            return (MongoTemplate) context.getBean(normalized);
        } catch (Exception e) {
            throw new IllegalArgumentException("No MongoTemplate configured for node '" + normalized + "'", e);
        }
    }

    public void checkNodeConnectivity(String nodeName) {
        String normalized = hashService.normalizeNodeName(nodeName);
        MongoTemplate template = getTemplateForNode(normalized);
        try {
            Document doc = template.executeCommand("{ ping: 1 }");
            if (doc == null || !doc.containsKey("ok")) {
                throw new IllegalStateException("Ping returned invalid response");
            }
        } catch (Exception e) {
            String address = hashService.getNodeAddress(normalized);
            throw new IllegalStateException("MongoDB node '" + normalized + "' at " + address + " is unreachable: " + e.getMessage(), e);
        }
    }

    public boolean isNodeReachable(String nodeName) {
        try {
            checkNodeConnectivity(nodeName);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static class StudentRecord {
        final Student student;
        final String sourceNode;

        StudentRecord(Student student, String sourceNode) {
            this.student = student;
            this.sourceNode = sourceNode;
        }
    }
}