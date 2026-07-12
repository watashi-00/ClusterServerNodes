package hexacloud.core.ports;

import hexacloud.core.contracts.ImplCluster;
import hexacloud.core.contracts.ImplSchedueler;
import hexacloud.core.contracts.Implserver;


public interface GatewayPort extends ImplSchedueler, ImplCluster, Implserver{}
