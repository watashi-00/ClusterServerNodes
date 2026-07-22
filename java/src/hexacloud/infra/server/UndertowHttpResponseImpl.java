package hexacloud.infra.server;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import io.undertow.util.Headers;
import hexacloud.core.server.filter.HttpResponse;
import java.io.PrintWriter;

public class UndertowHttpResponseImpl implements HttpResponse {

    private final HttpServerExchange exchange;
    private PrintWriter writer;

    public UndertowHttpResponseImpl(HttpServerExchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public void setHeader(String name, String value) {
        exchange.getResponseHeaders().put(HttpString.tryFromString(name), value);
    }

    @Override
    public void setStatus(int statusCode) {
        exchange.setStatusCode(statusCode);
    }

    @Override
    public void setContentType(String contentType) {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, contentType);
    }

    @Override
    public PrintWriter getWriter() throws Exception {
        if (writer == null) {
            if (!exchange.isResponseStarted()) {
                exchange.setStatusCode(200);
            }
            writer = new PrintWriter(exchange.getOutputStream(), true);
        }
        return writer;
    }

    @Override
    public boolean isCommitted() {
        return exchange.isResponseStarted();
    }
    
    public void flushBuffer() {
        if (writer != null) {
            writer.flush();
        }
    }
}
