package com.example.consistentHashing.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Student {

    @Id
    @JsonProperty("id")
    @JsonAlias({"rollNo", "studentId"})
    private String id;

    private String name;

    private String department;

    // Roll number compatibility helper
    @JsonProperty("rollNo")
    public String getRollNo() {
        return this.id;
    }

    @JsonProperty("rollNo")
    public void setRollNo(String rollNo) {
        if (this.id == null || this.id.isBlank()) {
            this.id = rollNo;
        }
    }
}