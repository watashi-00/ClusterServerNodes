package hexacloud.core.utils.network;

public enum ContentType {
    APPLICATION_JSON("application/json"),
    TEXT_PLAIN("text/plain"),
    TEXT_HTML("text/html");

    private final String value;

    ContentType(String value) {
        this.value = value;
    }

    public String value() {
        return this.value;
    }

    @Override
    public String toString() {
        return this.value;
    }
}
