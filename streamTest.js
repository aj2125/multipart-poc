fetch("http://localhost:8080/stream-multipart")
  .then(response => response.body.getReader())
  .then(reader => {
    const decoder = new TextDecoder("utf-8");

    function readChunk() {
      return reader.read().then(({ done, value }) => {
        if (done) {
          console.log("✅ Done reading stream.");
          return;
        }
        const chunk = decoder.decode(value, { stream: true });
        console.log("⏬ Chunk received:", chunk);  // 👈 This line
        return readChunk();
      });
    }

    return readChunk();
  });
