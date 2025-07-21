import java.io.IOException;

public class WebPConverter {

    private static void runCommand(String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.inheritIO();  // Optional: prints ffmpeg logs
        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("FFmpeg failed with exit code " + exitCode);
        }
    }

    public static void convertWebP(String inputPath) throws IOException, InterruptedException {
        runCommand("ffmpeg", "-i", inputPath, "-c:v", "libwebp", "-lossless", "1", "output_lossless.webp");
        runCommand("ffmpeg", "-i", inputPath, "-c:v", "libwebp", "-q:v", "75", "output_lossy.webp");
        runCommand("ffmpeg", "-i", inputPath, "-vf", "scale=320:-1", "thumbnail.webp");
    }

    public static void main(String[] args) {
        try {
            convertWebP("input.png");  // Update path as needed
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}



import java.io.IOException;

public class AVIFConverter {

    private static void runCommand(String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.inheritIO();  // Optional: prints ffmpeg logs
        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("FFmpeg failed with exit code " + exitCode);
        }
    }

    public static void convertAVIF(String inputPath) throws IOException, InterruptedException {
        runCommand("ffmpeg", "-i", inputPath, "-c:v", "libaom-av1", "-crf", "0", "-b:v", "0", "output_lossless.avif");
        runCommand("ffmpeg", "-i", inputPath, "-c:v", "libaom-av1", "-crf", "30", "-b:v", "0", "output_lossy.avif");
        runCommand("ffmpeg", "-i", inputPath, "-vf", "scale=320:-1", "thumbnail.avif");
    }

    public static void main(String[] args) {
        try {
            convertAVIF("input.png");  // Update path as needed
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}


String ffmpegPath = "C:\\tools\\ffmpeg\\bin\\ffmpeg.exe";  // Use double backslashes

ProcessBuilder pb = new ProcessBuilder(ffmpegPath, "-i", inputPath, "-c:v", "libwebp", "-q:v", "75", "output.webp");



ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-i", "input.png", "-c:v", "libwebp", "output.webp");
pb.environment().put("PATH", "C:\\tools\\ffmpeg\\bin;" + System.getenv("PATH"));  // prepend custom ffmpeg path

✅ 1. Dockerfile (FFmpeg Installed at Build Time)

FROM eclipse-temurin:17-jdk-jammy as base

# Install ffmpeg
RUN apt-get update && \
    apt-get install -y ffmpeg && \
    rm -rf /var/lib/apt/lists/*

# Copy Spring Boot app (assume already built as a fat JAR)
WORKDIR /app
COPY target/myapp.jar app.jar

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]


✅ 2. Jenkinsfile Snippet (Building and Pushing to ECR)



pipeline {
  agent any
  environment {
    AWS_REGION = 'us-east-1'
    ECR_REPO = '123456789012.dkr.ecr.us-east-1.amazonaws.com/myapp'
    IMAGE_TAG = 'latest'
  }

  stages {
    stage('Build Docker Image') {
      steps {
        script {
          sh 'aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $ECR_REPO'
          sh 'docker build -t myapp:$IMAGE_TAG .'
          sh 'docker tag myapp:$IMAGE_TAG $ECR_REPO:$IMAGE_TAG'
          sh 'docker push $ECR_REPO:$IMAGE_TAG'
        }
      }
    }
  }
}






  


  ✅ Result
When the Jenkins job runs:

ffmpeg is installed inside the container

Your Java code can call it like:

java
Copy
Edit
ProcessBuilder pb = new ProcessBuilder("ffmpeg", ...);



package com.example.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;

public class ClasspathResourceExtractor {

    private static final Path CACHE_DIR = Paths.get(System.getProperty("java.io.tmpdir"), "spring-image-cache");

    /**
     * Extracts a classpath resource to a physical file with its original name.
     * Suitable for use with native tools like ffmpeg. Safe for deployment.
     *
     * @param resourcePath e.g. "/sample-data/input.png"
     * @return Path to temp file
     * @throws IOException if extraction fails
     */
    public static Path extractToTemp(String resourcePath) throws IOException {
        if (!Files.exists(CACHE_DIR)) {
            Files.createDirectories(CACHE_DIR);
        }

        String filename = Paths.get(resourcePath).getFileName().toString();
        Path target = CACHE_DIR.resolve(filename);

        try (InputStream is = ClasspathResourceExtractor.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }

            Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
            target.toFile().deleteOnExit(); // ensures cleanup on JVM exit
            return target;
        }
    }

    /**
     * Optional: Clean up all files created by this utility manually.
     */
    public static void cleanUp() {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(CACHE_DIR)) {
            for (Path file : stream) {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            System.err.println("Cleanup failed: " + e.getMessage());
        }
    }
}



Path ffmpegInput = ClasspathResourceExtractor.extractToTemp("/sample-data/image.png");
// Then use ffmpegInput.toAbsolutePath().toString()




