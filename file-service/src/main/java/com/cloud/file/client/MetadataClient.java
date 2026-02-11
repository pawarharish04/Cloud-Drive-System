package com.cloud.file.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
// import com.cloud.file.dto.MetadataDTO; // Need to share DTO or duplicate

@FeignClient(name = "metadata-service", url = "${metadata-service.url}")
public interface MetadataClient {
    
    @PostMapping("/metadata")
    void saveMetadata(@RequestBody Object metadata); // Using Object for simplicity here, ideally a shared DTO
}
