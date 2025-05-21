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
