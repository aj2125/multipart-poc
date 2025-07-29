import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class ChunkedHeaderEnforcerFilter extends OncePerRequestFilter implements Ordered {

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE; // Ensure this filter runs last
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        filterChain.doFilter(request, response); // Allow normal processing

        String path = request.getRequestURI();

        // Apply logic only for the intended endpoint
        if ("/api/image/stream".equals(path)) {
            System.out.println("[ChunkedHeaderEnforcerFilter] Target path matched: " + path);

            // Remove Content-Length to prevent conflict with chunked encoding
            response.setHeader("Content-Length", null);
            System.out.println("[ChunkedHeaderEnforcerFilter] Removed Content-Length header");

            // Enforce Transfer-Encoding: chunked
            response.setHeader("Transfer-Encoding", "chunked");
            System.out.println("[ChunkedHeaderEnforcerFilter] Set Transfer-Encoding: chunked");
        }
    }
}





import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<ChunkedHeaderEnforcerFilter> chunkedHeaderFixFilter() {
        FilterRegistrationBean<ChunkedHeaderEnforcerFilter> registrationBean = new FilterRegistrationBean<>();

        registrationBean.setFilter(new ChunkedHeaderEnforcerFilter());
        registrationBean.addUrlPatterns("/api/image/stream"); // Restrict to target path
        registrationBean.setOrder(Ordered.LOWEST_PRECEDENCE); // Run after all other filters

        return registrationBean;
    }
}
