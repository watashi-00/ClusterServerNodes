package hexacloud.core.server.filter.builtin;

import hexacloud.core.cluster.Cluster;
import hexacloud.core.server.filter.*;
import java.io.PrintWriter;

@Order(30)
public class TokenAuthFilter implements HttpFilter {
    private final Cluster cluster;

    public TokenAuthFilter(Cluster cluster) {
        this.cluster = cluster;
    }

    @Override
    public void doFilter(HttpRequest request, HttpResponse response, HttpFilterChain chain) throws Exception {
        String token = request.getHeader("X-Cluster-Token");
        if (token == null || token.isEmpty()) {
            String query = request.getQuery();
            if (query != null && query.contains("token=")) {
                for (String param : query.split("&")) {
                    if (param.startsWith("token=")) {
                        token = param.substring(6);
                        break;
                    }
                }
            }
        }

        if (!cluster.authenticate(token)) {
            response.setHeader("Connection", "close");
            response.setStatus(401);
            try (PrintWriter writer = response.getWriter()) {
                writer.print("401 Unauthorized - Invalid or Missing API Token");
            }
            return;
        }
        chain.doFilter(request, response);
    }
}
