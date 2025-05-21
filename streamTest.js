fetch("http://localhost:8080/stream-multipart")
  .then(response => {
    console.log("✅ Got 200 response — beginning to stream...");
    const reader = response.body.getReader();
    const decoder = new TextDecoder("utf-8");

    function readChunk() {
      return reader.read().then(({ done, value }) => {
        if (done) {
          console.log("✅ Stream finished.");
          return;
        }

        const chunk = decoder.decode(value, { stream: true });
        console.log("⏬ Chunk received:", chunk);
        return readChunk();
      });
    }

    return readChunk();
  });
