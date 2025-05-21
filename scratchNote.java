import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStream;

@RestController
public class SpringStyleStreamingController {

    @GetMapping("/spring-stream")
    public ResponseEntity<StreamingResponseBody> springStream() {

        StreamingResponseBody stream = outputStream -> {
            for (int i = 0; i < 3; i++) {
                String chunk = "Spring Chunk " + i + "\n";
                outputStream.write(chunk.getBytes());
                outputStream.flush();  // ✅ Flush attempt
                System.out.println("[Spring] Flushed: " + chunk);
                Thread.sleep(1000);    // simulate delay between chunks
            }
        };

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        // You can try forcing this, but Spring/Tomcat might ignore it
        // headers.set("Transfer-Encoding", "chunked");

        return new ResponseEntity<>(stream, headers, HttpStatus.OK);
    }
}
