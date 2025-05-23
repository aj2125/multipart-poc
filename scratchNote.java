package com.example.multipartstreaming;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@RestController
public class StreamingMultipartFormDataController {

    @GetMapping("/multipart-data")
    public void streamMultipartFormData(HttpServletResponse response) throws IOException {
        String boundary = "form-boundary";

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("multipart/form-data; boundary=" + boundary);
        response.setHeader("Transfer-Encoding", "chunked");

        ServletOutputStream out = response.getOutputStream();

        // Part 1: JSON field
        out.write(("--" + boundary + "\r\n").getBytes());
        out.write("Content-Disposition: form-data; name=\"json_data\"\r\n".getBytes());
        out.write("Content-Type: application/json\r\n\r\n".getBytes());
        out.write("{\"message\": \"progressive form-data\"}\r\n".getBytes());
        out.flush();

        // Simulate delay
        try { Thread.sleep(1000); } catch (InterruptedException ignored) {}

        // Part 2: image field
        out.write(("--" + boundary + "\r\n").getBytes());
        out.write("Content-Disposition: form-data; name=\"image_data\"; filename=\"image.jpg\"\r\n".getBytes());
        out.write("Content-Type: image/jpeg\r\n\r\n".getBytes());

        try (InputStream in = new FileInputStream("src/main/resources/static/image.jpg")) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                out.flush(); // enable progressive streaming
            }
        }

        // Final boundary
        out.write(("\r\n--" + boundary + "--\r\n").getBytes());
        out.flush();
        out.close();
    }
}


// 2. Service that actually calls your producer and handles the parts
@Service
public class MultipartConsumerService {

    private final RestTemplate restTemplate;

    // inject via @Autowired or constructor
    public MultipartConsumerService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void fetchAndProcess() throws IOException {
        String url = "http://localhost:8080/multipart-data";

        // set Accept header (optional—producer will set Content-Type anyway)
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.MULTIPART_FORM_DATA));
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        // Tell RestTemplate to expect a MultiValueMap of Objects
        ResponseEntity<MultiValueMap<String,Object>> resp =
            restTemplate.exchange(
                url,
                HttpMethod.GET,
                requestEntity,
                new ParameterizedTypeReference<MultiValueMap<String,Object>>(){}
            );

        MultiValueMap<String,Object> parts = resp.getBody();
        if (parts == null) {
            throw new IllegalStateException("No body in response");
        }

        // ---- JSON part ----
        String jsonString = (String) parts.getFirst("json_data");
        ObjectMapper mapper = new ObjectMapper();
        Map<String,String> jsonData =
            mapper.readValue(jsonString, new TypeReference<Map<String,String>>(){});
        System.out.println("Name: " + jsonData.get("name"));
        System.out.println("Description: " + jsonData.get("description"));

        // ---- Image part ----
        // AllEncompassingFormHttpMessageConverter will present file parts as a Resource
        Resource imageResource = (Resource) parts.getFirst("image_data");
        byte[] imageBytes = StreamUtils.copyToByteArray(imageResource.getInputStream());
        // you can also inspect content type:
        MediaType contentType = imageResource.getURL() != null
            ? MediaType.IMAGE_PNG
            : MediaType.APPLICATION_OCTET_STREAM;

        // e.g., write to disk, pass to another service, etc.
        Files.write(Paths.get("downloaded.png"), imageBytes);
        System.out.println("Saved image (“downloaded.png”), type=" + contentType);
    }
}

// 1. Configuration to register the multipart converter
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            // Force these converters (order can matter)
            .messageConverters(
                // for JSON ↔ POJO
                new MappingJackson2HttpMessageConverter(),
                // for binary (byte[]) payloads
                new ByteArrayHttpMessageConverter(),
                // the AllEncompassingForm converter supports multipart/form-data
                new AllEncompassingFormHttpMessageConverter()
            )
            .build();
    }
}

