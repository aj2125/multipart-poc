import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class WebPCompressor {

    private static final String INPUT_DIR = "sample-input";
    private static final String OUTPUT_DIR = "output-images";

    /**
     * Entry method to handle compression from a file inside resources/sample-input
     * and save to resources/output-images.
     *
     * @param inputFileName Filename of the input image (e.g., "car.png")
     * @throws IOException if processing fails
     */
    public static void compressImageFromResources(String inputFileName) throws IOException {
        // Resolve input path
        URL inputUrl = WebPCompressor.class.getClassLoader().getResource(INPUT_DIR + "/" + inputFileName);
        if (inputUrl == null) {
            throw new IllegalArgumentException("File not found in resources/sample-input: " + inputFileName);
        }
        File inputFile = new File(inputUrl.getFile());

        // Ensure output directory exists
        Path outputDir = Paths.get("src/main/resources", OUTPUT_DIR);
        Files.createDirectories(outputDir);

        // Define output files
        String baseName = inputFileName.substring(0, inputFileName.lastIndexOf('.'));
        File lossyOutput = outputDir.resolve(baseName + "_lossy.webp").toFile();
        File losslessOutput = outputDir.resolve(baseName + "_lossless.webp").toFile();

        // Compress and save
        compressToWebP(inputFile, lossyOutput, losslessOutput);
        System.out.println("Compression complete: " + inputFileName);
    }

    /**
     * Compresses input image to WebP lossy and lossless.
     *
     * @param inputFile        PNG or compatible file with alpha
     * @param outputLossyFile  Output WebP file (lossy)
     * @param outputLosslessFile Output WebP file (lossless)
     * @throws IOException if error occurs
     */
    private static void compressToWebP(File inputFile, File outputLossyFile, File outputLosslessFile) throws IOException {
        BufferedImage image = ImageIO.read(inputFile);

        // Write WebP Lossy
        ImageIO.write(image, "webp", outputLossyFile);

        // Write WebP Lossless (same encoder — decoder decides mode based on context)
        ImageIO.write(image, "webp", outputLosslessFile);
    }

    public static void main(String[] args) {
        try {
            // Replace with the name of your file in sample-input
            compressImageFromResources("car.png");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}




import java.io.*;
import java.net.URL;
import java.nio.file.*;

public class AvifCompressor {

    private static final String INPUT_DIR = "sample-input";
    private static final String OUTPUT_DIR = "output-images";

    /**
     * Compress the given image to AVIF using FFmpeg subprocess.
     *
     * @param inputFileName File name inside sample-input/ (e.g., "car.png")
     * @throws IOException if any I/O error occurs
     * @throws InterruptedException if ffmpeg is interrupted
     */
    public static void compressToAvif(String inputFileName) throws IOException, InterruptedException {
        // Resolve the input file from resources
        URL inputUrl = AvifCompressor.class.getClassLoader().getResource(INPUT_DIR + "/" + inputFileName);
        if (inputUrl == null) {
            throw new IllegalArgumentException("File not found in sample-input: " + inputFileName);
        }
        File inputFile = new File(inputUrl.getFile());

        // Ensure output directory exists
        Path outputDir = Paths.get("src/main/resources", OUTPUT_DIR);
        Files.createDirectories(outputDir);

        // Output file path
        String baseName = inputFileName.substring(0, inputFileName.lastIndexOf('.'));
        File outputFile = outputDir.resolve(baseName + ".avif").toFile();

        // FFmpeg command
        ProcessBuilder pb = new ProcessBuilder(
            "ffmpeg",
            "-i", inputFile.getAbsolutePath(),
            "-c:v", "libaom-av1",
            "-crf", "30",          // adjust quality (lower = better quality)
            "-b:v", "0",           // use constant quality mode
            outputFile.getAbsolutePath()
        );

        // Pipe output
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Print ffmpeg logs to console
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("FFmpeg failed with exit code: " + exitCode);
        }

        System.out.println("AVIF Compression complete: " + outputFile.getName());
    }

    public static void main(String[] args) {
        try {
            compressToAvif("car.png");  // 🔄 Replace with your input file
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
