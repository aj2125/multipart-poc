import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

@RestController
@RequestMapping("/vehicle")
public class VehicleExportController {

    @GetMapping(value = "/export", produces = MediaType.MULTIPART_MIXED_VALUE)
    public ResponseEntity<MultiValueMap<String, Object>> exportVehicleData() throws IOException {
        // 1. Build the JSON part
        Map<String, Object> garagedVehicle = Map.of(
                "make", "Toyota",
                "model", "Camry",
                "year", 2020
        );
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> jsonPart = new HttpEntity<>(garagedVehicle, jsonHeaders);

        // 2. Load PNG file from disk or resource folder (for demo purposes)
        byte[] imageBytes = Files.readAllBytes(Paths.get("src/main/resources/sample.png"));
        ByteArrayResource imageResource = new ByteArrayResource(imageBytes);

        HttpHeaders imageHeaders = new HttpHeaders();
        imageHeaders.setContentType(MediaType.IMAGE_PNG);
        imageHeaders.setContentDisposition(ContentDisposition.attachment().filename("vehicle.png").build());
        HttpEntity<ByteArrayResource> imagePart = new HttpEntity<>(imageResource, imageHeaders);

        // 3. Build the multipart body
        MultiValueMap<String, Object> multipartBody = new LinkedMultiValueMap<>();
        multipartBody.add("garagedVehicle", jsonPart);
        multipartBody.add("vehicleImage", imagePart);

        return ResponseEntity.ok()
                .contentType(MediaType.MULTIPART_MIXED)
                .body(multipartBody);
    }
}
