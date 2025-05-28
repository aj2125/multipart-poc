// AwsS3Config.java
package com.example.config;

import java.time.Duration;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

@Configuration
public class AwsS3Config {

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    /** 
     * Bucket/key of a tiny object used purely to pre-warm DNS+TLS.
     * Configure in application.properties:
     *   s3.prewarm.bucket=my-bucket
     *   s3.prewarm.key=ping.txt
     */
    @Value("${s3.prewarm.bucket:}")
    private String prewarmBucket;

    @Value("${s3.prewarm.key:}")
    private String prewarmKey;

    @Bean
    public S3Client s3Client() {
        ApacheHttpClient.Builder httpClient = ApacheHttpClient.builder()
            .maxConnections(200)
            .connectionTimeout(Duration.ofMillis(500))
            .socketTimeout(Duration.ofSeconds(30))
            .connectionAcquireTimeout(Duration.ofMillis(500))
            .tcpKeepAlive(true)
            .useIdleConnectionReaper(true);

        return S3Client.builder()
            .region(Region.of(awsRegion))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .httpClientBuilder(httpClient)
            .overrideConfiguration(ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofSeconds(60))
                .apiCallAttemptTimeout(Duration.ofSeconds(30))
                .build())
            .build();
    }

    /**
     * Fire a HeadObject on startup to pay the DNS+TLS cost once.
     */
    @PostConstruct
    public void preWarmConnection() {
        if (prewarmBucket.isEmpty() || prewarmKey.isEmpty()) {
            return;
        }
        try {
            HeadObjectResponse meta = s3Client()
                .headObject(h -> h.bucket(prewarmBucket).key(prewarmKey));
            // Optionally log contentLength or contentType if desired
        } catch (Exception e) {
            // swallow—best effort only
        }
    }
}



// ImageController.java
package com.example.web;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

@RestController
public class ImageController {

    private final S3Client s3;

    public ImageController(S3Client s3Client) {
        this.s3 = s3Client;
    }

    @GetMapping("/images/{bucket}/{key:.+}")
    public void streamImage(
            @PathVariable String bucket,
            @PathVariable String key,
            HttpServletResponse response) {

        try {
            HeadObjectResponse meta = s3.headObject(h -> h.bucket(bucket).key(key));
            response.setContentType(meta.contentType());
            response.setHeader("Content-Length", String.valueOf(meta.contentLength()));
            response.setHeader("Cache-Control", "max-age=300, public");

            try (ResponseInputStream<GetObjectResponse> s3is =
                     s3.getObject(g -> g.bucket(bucket).key(key))) {
                StreamUtils.copy(s3is, response.getOutputStream());
            }
            response.flushBuffer();

        } catch (S3Exception e) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
        } catch (IOException e) {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }
}


@Configuration
@Profile({"dev", "test", "uat"})
@ConditionalOnProperty(name = "s3.prewarm.enabled", havingValue = "true", matchIfMissing = false)
public class S3PrewarmConfig {

    @Bean
    public ApplicationRunner s3Prewarmer(
        S3Client s3Client,
        @Value("${s3.prewarm.bucket}") String bucket,
        @Value("${s3.prewarm.key}") String key) {
      return args -> {
        if (!bucket.isEmpty() && !key.isEmpty()) {
          try {
            s3Client.headObject(h -> h.bucket(bucket).key(key));
          } catch (Exception e) {
            // swallow—best effort only
          }
        }
      };
    }
}

