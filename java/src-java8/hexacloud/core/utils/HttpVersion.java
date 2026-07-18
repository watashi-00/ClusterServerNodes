package hexacloud.core.utils;

public enum HttpVersion {
    HTTP_1_1,
    HTTP_2;

    public String resolveHttpVersion(HttpVersion config) {
        switch (config) {
            case HTTP_1_1:
                return "HTTP/1.1";
            case HTTP_2:
                return "HTTP/2";
            default:
                throw new IllegalArgumentException("Unsupported HTTP version");
        }
    }

    public static HttpVersion requestVersion(HttpVersion config) {
        switch (config) {
            case HTTP_1_1:
                return HTTP_1_1;
            case HTTP_2:
                return HTTP_2;
            default:
                throw new IllegalArgumentException("Unsupported HTTP version");
        }
    }
}
