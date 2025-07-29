import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.S3Client;
import java.io.InputStream;

@Service
public class S3ImageService {

    private final S3Client s3Client;

    @Value("${app.s3.bucket}")
    private String bucketName;

    public S3ImageService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public InputStream getImageStream(String s3Key) {
        GetObjectRequest request = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(s3Key)
            .build();

        return s3Client.getObject(request); // streaming InputStream
    }
}



import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;

@RestController
@RequestMapping("/api/images")
public class ImageController {

    private final S3ImageService s3ImageService;

    public ImageController(S3ImageService s3ImageService) {
        this.s3ImageService = s3ImageService;
    }

    @GetMapping(value = "/{imageKey}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> getImage(@PathVariable String imageKey) {

        InputStream imageStream = s3ImageService.getImageStream(imageKey);

        StreamingResponseBody body = outputStream -> {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = imageStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                outputStream.flush();
            }
            imageStream.close();
        };

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + imageKey + "\"")
            .header(HttpHeaders.TRANSFER_ENCODING, "chunked") // optional, but explicit
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(body);
    }
}


 StreamingResponseBody body = outputStream -> {
        byte[] buffer = new byte[8192];
        int bytesRead;
        try {
            while ((bytesRead = imageStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                outputStream.flush();  // 🚀 critical
            }
        } finally {
            imageStream.close();
        }
    };

    final long timeEnded = System.currentTimeMillis();

    return ResponseEntity.status(HttpStatus.OK)
            .header(HttpHeaders.TRANSFER_ENCODING, "chunked")
            .header("Accept-Ranges", "bytes")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(body);


return ResponseEntity.ok()
    .contentType(MediaType.APPLICATION_OCTET_STREAM)
    .body((StreamingResponseBody) outputStream -> {
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = imageStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        outputStream.flush();  // good practice
        imageStream.close();   // optional but safe
    });








        StreamingResponseBody responseBody = outputStream -> {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = imageStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                outputStream.flush();  // ✅ Critical to avoid buffering delay
            }
            imageStream.close();
        };

        long timeEnded = System.currentTimeMillis();
        log.info("Stream setup done in {}ms for UUID {}", (timeEnded - timeStarted), );

        // ✅ Optional: force commit response on some servlet engines
        try {
            HttpServletResponse rawResponse = ((ServletRequestAttributes) RequestContextHolder
                    .getRequestAttributes())
                    .getResponse();
            if (rawResponse != null) rawResponse.flushBuffer();
        } catch (Exception e) {
            log.warn("Could not flush servlet buffer", e);
        }

        return ResponseEntity
                .ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(responseBody);
