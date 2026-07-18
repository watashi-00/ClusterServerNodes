package hexacloud.core.utils;

import java.net.http.HttpClient;

public enum HttpVersion {
    HTTP_1_1,
    HTTP_2;

    public HttpClient.Version resolveHttpVersion(HttpVersion config) {
        return switch (config) {
            case HTTP_1_1 -> HttpClient.Version.HTTP_1_1;
            case HTTP_2 -> HttpClient.Version.HTTP_2;
        };
    }

    public static HttpVersion requestVersion(HttpVersion config) {
        return switch (config) {
            case HTTP_1_1 -> HTTP_1_1;
            case HTTP_2 -> HTTP_2;
        };
    }
}
