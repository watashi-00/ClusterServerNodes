package hexacloud;

import hexacloud.server.Cluster;

public class Main {
    
    public static void main(String[] args) {
        System.out.println("hello from Main");
        new Main().start();
    }

    public void start() {
        Cluster c1 = new Cluster();
        c1.setClusterName("Cluster1");
        c1.start(8080, false);
    }
}