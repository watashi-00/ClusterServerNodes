package hexacloud.core.server.route;

import java.io.PrintWriter;
import hexacloud.core.cluster.Cluster;
import hexacloud.core.model.ServerNode;

public class ClusterController implements RouteController {

    private final Cluster cluster;

    public ClusterController(Cluster cluster) {
        this.cluster = cluster;
    }

    @RouteMapping("GET_NODES")
    public void getNodes(String args, PrintWriter out) {
        StringBuilder sb = new StringBuilder();
        for(ServerNode node : this.cluster.getCluster()) {
            sb.append(node.getFullHost()).append("=").append(node.status()).append(";");
        }
        out.println(sb.toString());
    }

    @RouteMapping("REGISTER")
    public void register(String args, PrintWriter out) {
        try {
            int regPort = Integer.parseInt(args);
            cluster.registerServer(regPort);
            out.println("SUCCESS: Node port " + regPort + " registered.");
        } catch(NumberFormatException e) {
            out.println("ERROR: Invalid port format: " + args);
        }
    }
}
