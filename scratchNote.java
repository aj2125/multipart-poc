@GetMapping(value = "/stream-multipart", produces = MediaType.MULTIPART_MIXED_VALUE)
public ResponseEntity<StreamingResponseBody> streamMultipart() {
    StreamingResponseBody body = outputStream -> {
        String boundary = "myboundary";
        String jsonPart = "{\"name\": \"example\", \"description\": \"This is JSON\"}";

        // Write JSON part
        outputStream.write(("--" + boundary + "\r\n").getBytes());
        outputStream.write("Content-Type: application/json\r\n\r\n".getBytes());
        outputStream.write(jsonPart.getBytes());
        outputStream.write("\r\n".getBytes());
        outputStream.flush();  // ✅ Critical to stream it early

        // Simulate delay to mimic streaming effect
        Thread.sleep(1000);

        // Load image
        File image = new File("src/main/resources/static/image1.png");
        FileInputStream fis = new FileInputStream(image);

        // Write image part
        outputStream.write(("--" + boundary + "\r\n").getBytes());
        outputStream.write("Content-Type: image/png\r\n".getBytes());
        outputStream.write(("Content-Disposition: attachment; filename=\"image1.png\"\r\n\r\n").getBytes());

        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = fis.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
            outputStream.flush(); // ✅ Streaming!
        }
        fis.close();

        // End boundary
        outputStream.write(("\r\n--" + boundary + "--\r\n").getBytes());
        outputStream.flush();
    };

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("multipart/mixed; boundary=myboundary"));

    return new ResponseEntity<>(body, headers, HttpStatus.OK);
}
