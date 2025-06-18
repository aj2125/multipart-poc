

, produces = MediaType.MULTIPART_MIXED_VALUE)
resource "aws_api_gateway_rest_api" "vehicle_api" {
  name = "vehicle-api"

  binary_media_types = [
    "multipart/mixed",
    "multipart/form-data",
    "image/png",
    "image/jpeg",
    "image/jpg",
    "image/webp",
    "image/gif",
    "image/svg+xml",
    "application/pdf",
    "application/zip",
    "application/octet-stream"
  ]
}



@GetMapping(value = "/stream-multipart", produces = MediaType.MULTIPART_MIXED_VALUE)
public void streamMultipart(HttpServletResponse response) throws IOException {
    String boundary = "myboundary";

    // Set headers manually
    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("multipart/mixed; boundary=" + boundary);
    response.setHeader("Transfer-Encoding", "chunked");

    ServletOutputStream outputStream = response.getOutputStream();

    // --- Part 1: JSON ---
    outputStream.write(("--" + boundary + "\r\n").getBytes());
    outputStream.write("Content-Type: application/json\r\n\r\n".getBytes());
    outputStream.write("{\"message\": \"hello\", \"step\": 1}\r\n".getBytes());
    outputStream.flush();
    System.out.println("[Server] Flushed JSON");

    // --- Delay ---
    try {
        Thread.sleep(1000);  // Simulate streaming delay
    } catch (InterruptedException ignored) {}

    // --- Part 2: Image ---
    outputStream.write(("--" + boundary + "\r\n").getBytes());
    outputStream.write("Content-Type: image/png\r\n".getBytes());
    outputStream.write("Content-Disposition: attachment; filename=\"image.png\"\r\n\r\n".getBytes());

    try (InputStream imageStream = new FileInputStream("src/main/resources/static/image.png")) {
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = imageStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
            outputStream.flush();
        }
    }
    outputStream.write("\r\n".getBytes());
    System.out.println("[Server] Flushed image");

    // --- End boundary ---
    outputStream.write(("--" + boundary + "--\r\n").getBytes());
    outputStream.flush();
    outputStream.close();
    System.out.println("[Server] Stream completed");
}
