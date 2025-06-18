

, produces = MediaType.MULTIPART_MIXED_VALUE)
resource "aws_api_gateway_rest_api" "vehicle_api" {
  name = "vehicle-api"

  binary_media_types = [
    "multipart/mixed",
    "multipart/form-data",
    "image/png",
    "image/jpeg",
    "image/jpg",
    "image/webp",
    "image/gif",
    "image/svg+xml",
    "application/pdf",
    "application/zip",
    "application/octet-stream"
  ]
}

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

@RestController
public class VehicleExportController {

    @GetMapping(value = "/vehicle/export", produces = MediaType.MULTIPART_MIXED_VALUE)
    public ResponseEntity<MultiValueMap<String, Object>> exportVehicle() throws Exception {
        // Part 1: JSON metadata
        Map<String, Object> garagedVehicle = Map.of(
                "make", "Toyota",
                "model", "Camry",
                "year", 2021
        );
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> jsonPart = new HttpEntity<>(garagedVehicle, jsonHeaders);

        // Part 2: PNG image
        byte[] imageBytes = Files.readAllBytes(Paths.get("src/main/resources/sample.png"));
        ByteArrayResource imageResource = new ByteArrayResource(imageBytes);
        HttpHeaders imageHeaders = new HttpHeaders();
        imageHeaders.setContentType(MediaType.IMAGE_PNG);
        imageHeaders.setContentDisposition(ContentDisposition.attachment().filename("vehicle.png").build());
        HttpEntity<ByteArrayResource> imagePart = new HttpEntity<>(imageResource, imageHeaders);

        // Build multipart body
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("garagedVehicle", jsonPart);
        body.add("vehicleImage", imagePart);

        return ResponseEntity.ok()
                .contentType(MediaType.MULTIPART_MIXED)
                .body(body);
    }
}


@GetMapping(value = "/stream-multipart", produces = MediaType.MULTIPART_MIXED_VALUE)
public void streamMultipart(HttpServletResponse response) throws IOException {
    String boundary = "myboundary";

    // Set headers manually
    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("multipart/mixed; boundary=" + boundary);
    response.setHeader("Transfer-Encoding", "chunked");

    ServletOutputStream outputStream = response.getOutputStream();

    // --- Part 1: JSON ---
    outputStream.write(("--" + boundary + "\r\n").getBytes());
    outputStream.write("Content-Type: application/json\r\n\r\n".getBytes());
    outputStream.write("{\"message\": \"hello\", \"step\": 1}\r\n".getBytes());
    outputStream.flush();
    System.out.println("[Server] Flushed JSON");

    // --- Delay ---
    try {
        Thread.sleep(1000);  // Simulate streaming delay
    } catch (InterruptedException ignored) {}

    // --- Part 2: Image ---
    outputStream.write(("--" + boundary + "\r\n").getBytes());
    outputStream.write("Content-Type: image/png\r\n".getBytes());
    outputStream.write("Content-Disposition: attachment; filename=\"image.png\"\r\n\r\n".getBytes());

    try (InputStream imageStream = new FileInputStream("src/main/resources/static/image.png")) {
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = imageStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
            outputStream.flush();
        }
    }
    outputStream.write("\r\n".getBytes());
    System.out.println("[Server] Flushed image");

    // --- End boundary ---
    outputStream.write(("--" + boundary + "--\r\n").getBytes());
    outputStream.flush();
    outputStream.close();
    System.out.println("[Server] Stream completed");
}
