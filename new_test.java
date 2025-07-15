<dependencies>
    <!-- Image I/O libraries -->
    <dependency>
        <groupId>com.twelvemonkeys.imageio</groupId>
        <artifactId>imageio-core</artifactId>
        <version>3.9.4</version>
    </dependency>

    <!-- WebP support -->
    <dependency>
        <groupId>com.luciad</groupId>
        <artifactId>luciad-webp-imageio</artifactId>
        <version>1.4</version>
    </dependency>

    <!-- AVIF support (experimental via TwelveMonkeys plugin or native encoders) -->
    <dependency>
        <groupId>org.bytedeco</groupId>
        <artifactId>javacv-platform</artifactId>
        <version>1.5.9</version>
    </dependency>

    <!-- AWS SDK for S3 -->
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>s3</artifactId>
        <version>2.25.13</version>
    </dependency>
</dependencies>


  @Service
public class ImageCompressionService {

    public byte[] compressToWebP(BufferedImage inputImage) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        ImageWriter writer = ImageIO.getImageWritersByMIMEType("image/webp").next();
        ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
        writer.setOutput(ios);

        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionType("Lossless");
        param.setCompressionQuality(1.0f); // max quality

        writer.write(null, new IIOImage(inputImage, null, null), param);

        ios.close();
        writer.dispose();

        return baos.toByteArray();
    }

    public byte[] compressToAVIF(File inputFile) throws IOException {
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputFile);
        grabber.start();

        FFmpegFrameRecorder recorder = new FFmpegFrameRecorder("output.avif", grabber.getImageWidth(), grabber.getImageHeight());
        recorder.setFormat("avif");
        recorder.setVideoCodec(avcodec.AV_CODEC_ID_AV1); // AVIF is based on AV1
        recorder.setVideoQuality(0); // lossless
        recorder.start();

        Frame frame;
        while ((frame = grabber.grabImage()) != null) {
            recorder.record(frame);
        }

        recorder.stop();
        grabber.stop();

        // Read output file
        return Files.readAllBytes(Paths.get("output.avif"));
    }

    public BufferedImage generateThumbnail(BufferedImage original, int width, int height) {
        Image tmp = original.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = resized.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();
        return resized;
    }
}


@Service
public class ImageVariantUploader {

    private final ImageCompressionService compressionService;
    private final S3Uploader s3Uploader;

    public ImageVariantUploader(ImageCompressionService compressionService, S3Uploader s3Uploader) {
        this.compressionService = compressionService;
        this.s3Uploader = s3Uploader;
    }

    public void processAndUpload(File originalImageFile, String baseKey) throws IOException {
        BufferedImage original = ImageIO.read(originalImageFile);

        // Upload original
        byte[] originalBytes = Files.readAllBytes(originalImageFile.toPath());
        s3Uploader.upload(baseKey + "/original.png", originalBytes, "image/png");

        // WebP
        byte[] webpBytes = compressionService.compressToWebP(original);
        s3Uploader.upload(baseKey + "/compressed.webp", webpBytes, "image/webp");

        // AVIF (optional - slower, experimental)
        byte[] avifBytes = compressionService.compressToAVIF(originalImageFile);
        s3Uploader.upload(baseKey + "/compressed.avif", avifBytes, "image/avif");

        // Thumbnail (WebP)
        BufferedImage thumbnail = compressionService.generateThumbnail(original, 180, 120);
        byte[] thumbnailWebP = compressionService.compressToWebP(thumbnail);
        s3Uploader.upload(baseKey + "/thumbnail.webp", thumbnailWebP, "image/webp");
    }
}


public class VehicleImageProcessingOrchestrator {

    private final ExecutorService mobileExecutor;
    private final ExecutorService desktopExecutor;
    private final ImageProcessingTaskFactory taskFactory;
    private final DBSaveCoordinator dbSaveCoordinator;

    public VehicleImageProcessingOrchestrator(
            ExecutorService mobileExecutor,
            ExecutorService desktopExecutor,
            ImageProcessingTaskFactory taskFactory,
            DBSaveCoordinator dbSaveCoordinator
    ) {
        this.mobileExecutor = mobileExecutor;
        this.desktopExecutor = desktopExecutor;
        this.taskFactory = taskFactory;
        this.dbSaveCoordinator = dbSaveCoordinator;
    }

    public void orchestrate(Vehicle vehicle, RequestMetadata metadata) {
        boolean isMobile = metadata.isMobileDevice();

        ImageProcessingTask mobileTask = taskFactory.createMobileTask(vehicle);
        ImageProcessingTask desktopTask = taskFactory.createDesktopTask(vehicle);

        Future<String> primaryS3UrlFuture = submitTask(isMobile, mobileTask, desktopTask);
        Future<String> secondaryS3UrlFuture = submitTask(!isMobile, desktopTask, mobileTask);

        waitAndSaveWhenReady(vehicle, primaryS3UrlFuture, secondaryS3UrlFuture);
    }

    private Future<String> submitTask(boolean condition, ImageProcessingTask taskIfTrue, ImageProcessingTask taskIfFalse) {
        return condition
                ? mobileExecutor.submit(taskIfTrue)
                : desktopExecutor.submit(taskIfFalse);
    }

    private void waitAndSaveWhenReady(Vehicle vehicle, Future<String> primaryFuture, Future<String> secondaryFuture) {
        try {
            String primaryS3Url = primaryFuture.get(); // Wait for main S3 image to complete
            vehicle.setS3ImageUrl(primaryS3Url);

            triggerDBSave(vehicle);

            // Optional: wait for secondary to complete, without blocking orchestration
            secondaryFuture.get(); // Fire and forget (can be improved with listener or async handler)

        } catch (InterruptedException | ExecutionException e) {
            // log + fallback
            throw new ImageProcessingException("Failed during image orchestration", e);
        }
    }

    private void triggerDBSave(Vehicle vehicle) {
        dbSaveCoordinator.persist(vehicle);
    }
}



private void triggerDbSaveWhenBothS3UploadsComplete(
        VehicleRequest req,
        ImageProcessingTask mobileTask,
        ImageProcessingTask desktopTask
) {
    waitForBothS3Urls(mobileTask, desktopTask)
        .thenAcceptAsync(pair -> triggerDbSave(req, pair.getLeft(), pair.getRight()), backgroundExecutor);
}

private CompletableFuture<Pair<String, String>> waitForBothS3Urls(
        ImageProcessingTask mobileTask,
        ImageProcessingTask desktopTask
) {
    return mobileTask.getS3UrlFuture()
            .thenCombine(desktopTask.getS3UrlFuture(), Pair::of);
}

private void triggerDbSave(VehicleRequest req, String mobileUrl, String desktopUrl) {
    saveToDbOrQueue(req, mobileUrl, desktopUrl);
}


public MultipartResponse handleVehicleImageProcessing(DeviceType deviceType, Request req) {
    ImageVariant preferredVariant = (deviceType == DeviceType.MOBILE)
        ? ImageVariant.MOBILE
        : ImageVariant.DESKTOP;

    ImageVariant nonPreferredVariant = (preferredVariant == ImageVariant.MOBILE)
        ? ImageVariant.DESKTOP
        : ImageVariant.MOBILE;

    // Choose executors — preferred gets higher-priority executor
    ExecutorService preferredExecutor = getPreferredExecutor();
    ExecutorService secondaryExecutor = getSecondaryExecutor();

    // Start both tasks
    ImageProcessingTask preferredTask = new ImageProcessingTask(preferredVariant, req, preferredExecutor);
    ImageProcessingTask secondaryTask = new ImageProcessingTask(nonPreferredVariant, req, secondaryExecutor);

    preferredTask.start();
    secondaryTask.start();

    // Await only preferred image fetch to respond fast
    ImageData imageForUi = preferredTask.getImageFuture().join();

    // Respond to UI
    MultipartResponse response = buildMultipartResponse(req, imageForUi);

    // Fire off DB/SQS save after both S3 URLs are ready
    triggerCombinedDbSave(req, preferredTask, secondaryTask);

    return response;
}


@Autowired
private ExecutorService multipartExecutor;

@Autowired
private ExecutorService backgroundExecutor;

private ExecutorService getPreferredExecutor(DeviceType deviceType) {
    return multipartExecutor; // Always use this for preferred image (mobile or desktop)
}

private ExecutorService getNonPreferredExecutor(DeviceType deviceType) {
    return backgroundExecutor; // Offload non-critical image fetch to background
}


public MultipartResponse handleVehicleImageProcessing(DeviceType deviceType, Request req) {
    // Dedicated executor for user-facing preferred image path
    ExecutorService multipartExecutor = preferredExecutorService(); // e.g. ThreadPoolExecutor(15)

    // Step 1: Create both tasks
    ImageProcessingTask mobileTask = createImageProcessingTask(ImageVariant.MOBILE, req, multipartExecutor);
    ImageProcessingTask desktopTask = createImageProcessingTask(ImageVariant.DESKTOP, req, backgroundExecutorService);

    // Step 2: Start both in parallel
    mobileTask.start();   // runs on multipartExecutor
    desktopTask.start();  // runs on backgroundExecutor

    // Step 3: Wait only for preferred image (non-blocking desktop)
    ImageData preferredImage = waitForPreferredImage(deviceType, mobileTask, desktopTask);

    // Step 4: Return preferred image in multipart (with metadata JSON)
    MultipartResponse response = buildMultipartResponse(req, preferredImage);

    // Step 5: Kick off async DB/SQS save *after both S3 uploads are ready*
    mobileTask.getS3UrlFuture()
        .thenCombineAsync(desktopTask.getS3UrlFuture(), (mobileUrl, desktopUrl) -> {
            saveToDbOrQueue(req, mobileUrl, desktopUrl);
            return null;
        }, backgroundExecutorService);  // optional: explicit offloading

    return response;
}
public MultipartResponse handleVehicleImageProcessing(DeviceType deviceType, Request req) {
    // Step 1: Create tasks for both mobile and desktop
    ImageProcessingTask mobileTask = createImageProcessingTask(ImageVariant.MOBILE, req);
    ImageProcessingTask desktopTask = createImageProcessingTask(ImageVariant.DESKTOP, req);

    // Step 2: Start both fetch+save tasks immediately
    mobileTask.start();   // returns CompletableFuture internally
    desktopTask.start();

    // Step 3: Wait for preferred image (e.g., mobile for mobile devices)
    ImageData preferredImage = waitForPreferredImage(deviceType, mobileTask, desktopTask);

    // Step 4: Return preferred image + JSON metadata immediately (multipart response)
    MultipartResponse response = buildMultipartResponse(req, preferredImage);

    // Step 5: Trigger DB/SQS update *after* both S3 uploads are ready
    mobileTask.getS3UrlFuture()
        .thenCombineAsync(desktopTask.getS3UrlFuture(), (mobileUrl, desktopUrl) -> {
            saveToDbOrQueue(req, mobileUrl, desktopUrl);
            return null;
        });

    return response;
}
// Decide preferred and secondary variant
ImageVariant preferredVariant = deviceType.isMobile() ? ImageVariant.MOBILE : ImageVariant.DESKTOP;
ImageVariant secondaryVariant = deviceType.isMobile() ? ImageVariant.DESKTOP : ImageVariant.MOBILE;

// Create tasks accordingly
ImageProcessingTask preferredTask = new ImageProcessingTask(preferredVariant, req, preferredExecutor);
ImageProcessingTask otherTask = new ImageProcessingTask(secondaryVariant, req, backgroundExecutor);
