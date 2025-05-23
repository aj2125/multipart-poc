
@Configuration
public class HttpClientConfig {
  @Bean
  public OkHttpClient okHttpClient() {
    return new OkHttpClient.Builder()
      .protocols(List.of(Protocol.HTTP_2, Protocol.HTTP_1_1))
      .connectionPool(new ConnectionPool(50, 5, TimeUnit.MINUTES))
      .build();
  }
}



@Configuration
public class HttpClientConfig {
  @Bean
  public OkHttpClient okHttpClient(Proxy corporateProxy) {
    return new OkHttpClient.Builder()
      // tune your pool to match your concurrency profile
      .connectionPool(new ConnectionPool(50, 5, TimeUnit.MINUTES))
      .proxy(corporateProxy)
      // optional: tighten timeouts
      .connectTimeout(2, TimeUnit.SECONDS)
      .readTimeout(10, TimeUnit.SECONDS)
      .retryOnConnectionFailure(true)
      .build();
  }
}


OkHttpClient ok = new OkHttpClient.Builder()
  .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("proxy.corp", 8080)))
  .proxyAuthenticator((route, response) -> /* creds */)
  .build();



@Component
public class Warmup {
  public Warmup(OkHttpClient client) {
    client.newCall(new Request.Builder()
      .url("https://vendor-cdn.example.com/ping")
      .head()
      .build()).enqueue(/* no-op callback */);
  }
}



Cache<String, byte[]> thumbCache = Caffeine.newBuilder()
    .expireAfterWrite(5, TimeUnit.MINUTES)
    .maximumSize(1_000)
    .build();




public class CachingDns implements Dns {
  private final Cache<String, List<InetAddress>> cache =
    Caffeine.newBuilder()
      .expireAfterWrite(60, TimeUnit.SECONDS)
      .build();

  @Override
  public List<InetAddress> lookup(String hostname) throws UnknownHostException {
    return cache.get(hostname, h -> Dns.SYSTEM.lookup(h));
  }
}

// … in your OkHttpClient builder:
.dns(new CachingDns())


import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.time.Duration;

public class Warmup {

    /**
     * Fires off a single HEAD “ping” to the CDN to pre-establish DNS, TCP and TLS
     * on the shared OkHttpClient connection pool.
     * 
     * @param client an optimized OkHttpClient (keep-alive, pooling, HTTP/2 enabled)
     */
    public Warmup(OkHttpClient client) {
        Request ping = new Request.Builder()
            .url("https://vendor-cdn.example.com/ping")
            .head()  // only metadata, no body download
            .build();

        // Asynchronous enqueue to avoid blocking startup thread
        client.newCall(ping).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // No-op: warm-up failures are non-fatal
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                // Drain and close immediately to return the connection to the pool
                response.body().close();
            }
        });
    }
}

  
