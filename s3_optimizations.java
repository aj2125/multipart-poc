import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

public class S3ClientFactory {
    public static S3AsyncClient createClient() {
        SdkAsyncHttpClient netty = NettyNioAsyncHttpClient.builder()
            .enableHttp2(true)
            .maxConcurrency(64)
            .connectionTimeout(Duration.ofSeconds(5))
            .build();

        return S3AsyncClient.builder()
            .httpClient(netty)
            .region(Region.US_EAST_1)
            .build();
    }

    public static void warmUp(S3AsyncClient client) {
        client.getObject(GetObjectRequest.builder()
                .bucket("my-bucket")
                .key("small-metadata.json")
                .build())
            .whenComplete((resp, err) -> {
                if (err != null) System.err.println("Warm-up failed: " + err);
                else System.out.println("Warm-up OK");
            });
    }
}

// force DNS + TLS + connection pool fill
s3.getObject(GetObjectRequest.builder()
    .bucket(bucketName)
    .key("some-small-file-or-metadata")
    .build());


NettyNioAsyncHttpClient httpClient = NettyNioAsyncHttpClient.builder()
    .enableHttp2(true)
    .maxConcurrency(64)
    .build();
S3AsyncClient s3Async = S3AsyncClient.builder()
    .httpClient(httpClient)
    .region(Region.US_EAST_1)
    .build();
// force DNS + TLS + connection pool fill
s3.getObject(GetObjectRequest.builder()
    .bucket(bucketName)
    .key("some-small-file-or-metadata")
    .build());
InetAddress.getByName("s3.us-east-1.amazonaws.com");

