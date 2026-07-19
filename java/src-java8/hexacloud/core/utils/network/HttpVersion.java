package hexacloud.core.utils.network;

public enum HttpVersion {
    HTTP_1_1,
    HTTP_2;

    public Object resolveHttpVersion(HttpVersion config) {
        return null;
    }

    public static HttpVersion requestVersion(HttpVersion config) {
        switch (config) {
            case HTTP_1_1:
                return HTTP_1_1;
            case HTTP_2:
                return HTTP_2;
            default:
                return HTTP_1_1;
        }
    }
}
