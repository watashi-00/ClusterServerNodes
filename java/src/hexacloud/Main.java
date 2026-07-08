package hexacloud;



public class Main {
    
    public static void main(String[] args) {
        System.out.println("hello from Main");
        new hexacloud.server.Cluster().start(8080, false);
    }
}