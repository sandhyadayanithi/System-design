package com.example.consistentHashing.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MigrationItem {

    private String studentId;
    private String studentName;
    private String previousNode;
    private String previousNodeAddress;
    private String destinationNode;
    private String destinationNodeAddress;
    private String status; // SUCCESS or FAILED
    private String failureDetails;
}
