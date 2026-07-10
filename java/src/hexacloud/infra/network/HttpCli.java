package hexacloud.infra.network;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

class HttpCli {

    public HttpCli() {}

    void fetchPing(String host) {
        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(host))
                .GET()
                .build();

            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());

            if(res.statusCode() >= 200 && res.statusCode() < 300) {
                System.out.println(host + " online " + res.statusCode());
            } else {
                System.out.println(host + " online, but instable " + res.statusCode());
            }

        } catch(Exception e) {
            System.out.println(host +" "+ e.getMessage());
        }
    }
}
