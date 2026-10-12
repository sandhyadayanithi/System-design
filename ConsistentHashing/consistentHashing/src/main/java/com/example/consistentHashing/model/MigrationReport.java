package com.example.consistentHashing.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MigrationReport {

    private String action; // ADD_NODE, REMOVE_NODE, NONE
    private String targetNode;
    private String targetNodeAddress;
    private String status; // SUCCESS, FAILED, PARTIAL_FAILURE, NO_MIGRATIONS_RUN
    private String message;
    private String timestamp;
    private int totalExamined;
    private int migratedCount;
    private int unchangedCount;
    private int failedCount;

    @Builder.Default
    private List<String> activeNodes = new ArrayList<>();

    @Builder.Default
    private List<MigrationItem> migratedStudents = new ArrayList<>();
}
