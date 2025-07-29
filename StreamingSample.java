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




@RestController
@RequestMapping("/api/v1/images")
public class ImageStreamController {

    private final ImageService imageService;

    public ImageStreamController(ImageService imageService) {
        this.imageService = imageService;
    }

    @PostMapping(value = "/streamById", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> streamImageById(
            @RequestHeader("request-id") String requestId,
            @RequestHeader("channel") String channel,
            @RequestHeader("device-type") String deviceType,
            @RequestBody ImageRequest imageRequest
    ) {
        log.info("Start streaming image for imageId: {}", imageRequest.getImageIdentifier());
        final long startTime = System.currentTimeMillis();

        InputStream imageStream = imageService.getImageStreamById(imageRequest);

        try {
            int available = imageStream.available();
            log.info("Available bytes in image stream: {}", available);
        } catch (IOException e) {
            log.warn("Unable to determine available bytes in image stream", e);
        }

        StreamingResponseBody responseBody = outputStream -> {
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;

            try {
                while ((bytesRead = imageStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    outputStream.flush();
                    totalBytes += bytesRead;
                    log.info("Streamed {} bytes so far", totalBytes);
                }
            } finally {
                imageStream.close();
                log.info("Finished streaming. Total bytes sent: {}", totalBytes);
            }
        };

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Completed streaming in {} ms for imageId: {}", elapsed, imageRequest.getImageIdentifier());

        return ResponseEntity.ok()
                .header(HttpHeaders.TRANSFER_ENCODING, "chunked")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(responseBody);
    }
}










StreamingResponseBody responseBody = outputStream -> {
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;

            try (imageStream) {
                while ((bytesRead = imageStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    outputStream.flush();               // Flush to container buffer
                    ((HttpServletResponse) ((ServletRequestAttributes) RequestContextHolder
                        .getRequestAttributes()).getResponse()).flushBuffer();  // Force flush to client
                    totalBytes += bytesRead;
                    System.out.println("Streamed " + totalBytes + " bytes so far");
                }
            } catch (IOException e) {
                System.err.println("Streaming error: " + e.getMessage());
            }

            System.out.println("Finished streaming. Total bytes sent: " + totalBytes);
        };

        return ResponseEntity
                .ok()
                .header(HttpHeaders.TRANSFER_ENCODING, "chunked")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(responseBody);











//servlet modification




@RestController
@RequestMapping("/images")
public class ImageController {

    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    @GetMapping("/{imageId}")
    public ResponseEntity<StreamingResponseBody> streamImage(@PathVariable String imageId) {
        StreamingResponseBody streamBody = imageService.createStreamingResponse(imageId);
        return ResponseEntity
                .ok()
                .header(HttpHeaders.TRANSFER_ENCODING, "chunked")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(streamBody);
    }
}


@Service
public class ImageService {

    private static final int BUFFER_SIZE = 8192;
    private static final Logger logger = LoggerFactory.getLogger(ImageService.class);

    public StreamingResponseBody createStreamingResponse(String imageId) {
        InputStream imageStream = fetchImageStream(imageId);

        // Setup headers early in the request lifecycle
        setStreamingHeaders();

        return outputStream -> {
            long totalBytesSent = 0;
            byte[] buffer = new byte[BUFFER_SIZE];

            try (InputStream input = imageStream) {
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    outputStream.flush();
                    flushResponseToClient();
                    totalBytesSent += bytesRead;
                    logProgress(totalBytesSent);
                }
            } catch (IOException e) {
                logger.warn("Failed during streaming: {}", e.getMessage(), e);
            }

            logger.info("Streaming complete. Total bytes sent: {}", totalBytesSent);
        };
    }

    private InputStream fetchImageStream(String imageId) {
        // Replace with real S3 logic
        return new ByteArrayInputStream(new byte[0]); // Placeholder for demo
    }

    private void setStreamingHeaders() {
        HttpServletResponse response = getCurrentHttpServletResponse();
        if (response != null) {
            response.setHeader(HttpHeaders.TRANSFER_ENCODING, "chunked");
            response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
            // Optional: remove conflicting headers
            response.setHeader(HttpHeaders.CONTENT_LENGTH, null); // avoid "length + chunked" conflict
        }
    }

    private void flushResponseToClient() {
        HttpServletResponse response = getCurrentHttpServletResponse();
        if (response != null) {
            try {
                response.flushBuffer();
            } catch (IOException e) {
                logger.warn("Flush to client failed: {}", e.getMessage(), e);
            }
        }
    }

    private HttpServletResponse getCurrentHttpServletResponse() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getResponse() : null;
    }

    private void logProgress(long bytesSent) {
        logger.debug("Streamed {} bytes", bytesSent);
    }
}


@GetMapping("/api/image")
public void streamImage(@RequestParam("id") String id, HttpServletResponse response) {
    InputStream inputStream = null;

    try {
        // Fetch image stream from service layer
        inputStream = imageService.getImageStream(id);

        // Set response headers
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader("Transfer-Encoding", "chunked");
        response.setHeader("Cache-Control", "no-cache");

        // Start writing the stream to response
        OutputStream outputStream = response.getOutputStream();
        byte[] buffer = new byte[8192];
        int bytesRead;
        long totalBytes = 0;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
            outputStream.flush();   // Flush to container buffer
            response.flushBuffer(); // Force flush to client
            totalBytes += bytesRead;
            System.out.println("Streamed " + totalBytes + " bytes so far");
        }

        System.out.println("Finished streaming. Total bytes sent: " + totalBytes);

    } catch (IOException e) {
        e.printStackTrace();
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    } finally {
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException ignored) {}
        }
    }
}











@GetMapping(value = "/stream", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
public ResponseEntity<StreamingResponseBody> streamImage(@RequestBody ImageRequest request) {

    InputStream inputStream = imageService.getImageInputStream(request);

    StreamingResponseBody stream = outputStream -> {
        byte[] buffer = new byte[8192];
        int bytesRead;
        long totalBytes = 0;

        try (inputStream) {
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                outputStream.flush();               // Flush to container
                ((ServletRequestAttributes) RequestContextHolder
                        .getRequestAttributes())
                        .getResponse()
                        .flushBuffer();            // Flush to client
                totalBytes += bytesRead;
            }
        }

        System.out.println("Finished streaming. Total bytes sent: " + totalBytes);
    };

    return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            // DO NOT SET Content-Length or Transfer-Encoding here
            .cacheControl(CacheControl.noCache())
            .body(stream);
}


