@GetMapping("/test-chunks")
public void testChunks(HttpServletResponse response) throws IOException {
    response.setHeader("Transfer-Encoding", "chunked");
    response.setContentType("text/plain");

    ServletOutputStream out = response.getOutputStream();

    for (int i = 1; i <= 5; i++) {
        out.write(("Chunk " + i + "\n").getBytes());
        out.flush();
        System.out.println("Flushed chunk " + i);
        try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
    }

    out.close();
}


fetch('http://localhost:8080/test-chunks')
  .then(r => r.body.getReader())
  .then(reader => {
    const decoder = new TextDecoder();
    return (function read() {
      return reader.read().then(({ done, value }) => {
        if (done) return console.log('✅ done');
        console.log('⏬', decoder.decode(value));
        return read();
      });
    })();
  });


try (InputStream in = new FileInputStream("your_big_image.jpg")) {
    byte[] buffer = new byte[1024];
    int bytesRead;
    while ((bytesRead = in.read(buffer)) != -1) {
        out.write(buffer, 0, bytesRead);
        out.flush(); // force delivery
        Thread.sleep(100); // simulate chunked progression
    }
}

