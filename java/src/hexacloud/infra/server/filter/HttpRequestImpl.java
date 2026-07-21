package hexacloud.infra.server.filter;

import com.sun.net.httpserver.HttpExchange;
import hexacloud.core.server.filter.HttpRequest;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class HttpRequestImpl implements HttpRequest {
    private final HttpExchange exchange;
    private final Map<String, Object> attributes = new HashMap<>();

    public HttpRequestImpl(HttpExchange exchange) {
        this.exchange = exchange;
    }

    @Override public String getMethod() { return exchange.getRequestMethod(); }
    @Override public URI getRequestURI() { return exchange.getRequestURI(); }
    @Override public String getPath() { return exchange.getRequestURI().getPath(); }
    @Override public String getQuery() { return exchange.getRequestURI().getQuery(); }
    @Override public String getHeader(String name) { return exchange.getRequestHeaders().getFirst(name); }
    @Override public Map<String, List<String>> getHeaders() { return exchange.getRequestHeaders(); }
    @Override public String getClientIp() { return exchange.getRemoteAddress().getAddress().getHostAddress(); }
    @Override public void setAttribute(String key, Object value) { attributes.put(key, value); }
    @Override public Object getAttribute(String key) { return attributes.get(key); }
}
