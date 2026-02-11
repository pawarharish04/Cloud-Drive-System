package com.cloud.file.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CompleteUploadRequest {

    @NotBlank(message = "Upload ID is required")
    private String uploadId;
}
