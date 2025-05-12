package com.example.multiparttest.rest;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ResourceUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class MultipartResponseController {

    @GetMapping("/multipart-data")
    public ResponseEntity<MultiValueMap<String, Object>> getMultipartData() throws IOException {
        // JSON data
        Map<String, String> jsonData = new HashMap<>();
        jsonData.put("name", "example");
        jsonData.put("description", "This is an example");

        // Serialize JSON data
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonString = objectMapper.writeValueAsString(jsonData);

        // Image data (replace with your image path)
        String imagePath = "test-data/image2.png";  // Change this to the actual image path

        File file = ResourceUtils.getFile("classpath:"+imagePath);
        byte[] imageBytes = Files.readAllBytes(file.toPath());

        // Wrap image in a ByteArrayResource
        ByteArrayResource imageResource = new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return "test-data/image2.png"; // Optional, specify filename if needed
            }
        };

        // Build multipart response
        MultiValueMap<String, Object> multipartBody = new LinkedMultiValueMap<>();

        // Add JSON data to the response (as a string)
        multipartBody.add("json_data", jsonString);

        // Add image data to the response (with headers for image content type)
        HttpHeaders imageHeaders = new HttpHeaders();
        imageHeaders.setContentType(MediaType.IMAGE_PNG); // Set appropriate content type (adjust for your image type)
        multipartBody.add("image_data", new org.springframework.http.HttpEntity<>(imageResource, imageHeaders));

        // Set response headers
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.MULTIPART_FORM_DATA); // Specify multipart/form-data content type

        return ResponseEntity.ok()
                .headers(responseHeaders)
                .body(multipartBody);
    }
}
