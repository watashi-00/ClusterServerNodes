package hexacloud.infra.server;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import io.undertow.util.Headers;
import hexacloud.core.server.filter.HttpResponse;
import java.io.PrintWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

public class UndertowHttpResponseImpl implements HttpResponse {

    private final HttpServerExchange exchange;
    private PrintWriter writer;

    private java.io.StringWriter stringWriter;

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
            stringWriter = new java.io.StringWriter();
            writer = new PrintWriter(stringWriter);
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
            String content = stringWriter.toString();
            exchange.getResponseSender().send(content);
        }
    }
}
