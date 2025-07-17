@Service
public class ImageS3StorageService {

    private final AmazonS3 amazonS3;
    private final String bucketName = "your-bucket-name"; // inject via config

    public ImageS3StorageService(AmazonS3 amazonS3) {
        this.amazonS3 = amazonS3;
    }

    public S3Result saveImage(byte[] imageBytes, String vendorImageId, DeviceType deviceType, boolean compress) {
        String fileExtension = compress ? "webp" : "png";
        String s3Key = generateKey(vendorImageId, deviceType, fileExtension);

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(imageBytes.length);
        metadata.setContentType(getMimeType(fileExtension));

        ByteArrayInputStream inputStream = new ByteArrayInputStream(imageBytes);
        amazonS3.putObject(bucketName, s3Key, inputStream, metadata);

        String s3Url = amazonS3.getUrl(bucketName, s3Key).toString();
        return new S3Result(s3Key, s3Url, compress);
    }

    private String generateKey(String vendorImageId, DeviceType deviceType, String extension) {
        return String.format("images/%s/%s.%s", deviceType.name().toLowerCase(), vendorImageId, extension);
    }

    private String getMimeType(String extension) {
        return switch (extension) {
            case "webp" -> "image/webp";
            case "avif" -> "image/avif";
            default -> "image/png";
        };
    }
}public class VehicleImageProcessingTask implements Callable<S3Result> {

    private final byte[] imageBytes;
    private final String vendorImageId;
    private final DeviceType deviceType;
    private final boolean compress;
    private final ImageS3StorageService s3StorageService;

    public VehicleImageProcessingTask(
        byte[] imageBytes,
        String vendorImageId,
        DeviceType deviceType,
        boolean compress,
        ImageS3StorageService s3StorageService
    ) {
        this.imageBytes = imageBytes;
        this.vendorImageId = vendorImageId;
        this.deviceType = deviceType;
        this.compress = compress;
        this.s3StorageService = s3StorageService;
    }

    @Override
    public S3Result call() {
        return s3StorageService.saveImage(imageBytes, vendorImageId, deviceType, compress);
    }
}


@Service
public class ImageProcessorService {

    private final ExecutorService executor = Executors.newFixedThreadPool(4); // Tune as needed
    private final ImageS3StorageService s3StorageService;

    public ImageProcessorService(ImageS3StorageService s3StorageService) {
        this.s3StorageService = s3StorageService;
    }

    public Map<DeviceType, CompletableFuture<S3Result>> processImages(
        Map<DeviceType, byte[]> images,
        String vendorImageId,
        boolean compress
    ) {
        Map<DeviceType, CompletableFuture<S3Result>> futureMap = new EnumMap<>(DeviceType.class);

        for (Map.Entry<DeviceType, byte[]> entry : images.entrySet()) {
            VehicleImageProcessingTask task = new VehicleImageProcessingTask(
                entry.getValue(),
                vendorImageId,
                entry.getKey(),
                compress,
                s3StorageService
            );

            CompletableFuture<S3Result> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return task.call();
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }, executor);

            futureMap.put(entry.getKey(), future);
        }

        return futureMap;
    }

    public CompletableFuture<S3Result> awaitAndReturnOneWhenReady(Map<DeviceType, CompletableFuture<S3Result>> futureMap, DeviceType priorityDevice) {
        return futureMap.get(priorityDevice).thenApply(result -> {
            // trigger non-priority async (optional)
            futureMap.entrySet().stream()
                .filter(e -> !e.getKey().equals(priorityDevice))
                .forEach(e -> e.getValue().exceptionally(ex -> {
                    // log or handle silently
                    return null;
                }));
            return result;
        });
    }

    public void shutdown() {
        executor.shutdown();
    }
}


@RestController
@RequestMapping("/images")
public class ImageUploadController {

    private final ImageProcessorService imageProcessorService;

    public ImageUploadController(ImageProcessorService imageProcessorService) {
        this.imageProcessorService = imageProcessorService;
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadImages(@RequestBody Map<String, byte[]> inputMap) throws Exception {
        String vendorImageId = UUID.randomUUID().toString(); // Example

        Map<DeviceType, byte[]> images = Map.of(
            DeviceType.MOBILE, inputMap.get("mobile"),
            DeviceType.DESKTOP, inputMap.get("desktop")
        );

        Map<DeviceType, CompletableFuture<S3Result>> futures = imageProcessorService.processImages(images, vendorImageId, true);
        CompletableFuture<S3Result> responseFuture = imageProcessorService.awaitAndReturnOneWhenReady(futures, DeviceType.MOBILE);

        S3Result result = responseFuture.get(5, TimeUnit.SECONDS); // Block for max 5s
        return ResponseEntity.ok("Returned first available: " + result.getS3Url());
    }
}














