package com.example.cluster.nodelog;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.cluster.typed.Cluster;

public class NodeInfoLogger {
    public static String getNodeInfo(ActorContext<?> context) {
        ActorSystem<?> system = context.getSystem();
        Cluster cluster = Cluster.get(system);
        String host = cluster.selfMember().address().host().isDefined() ? cluster.selfMember().address().host().get() : "unknown";
        int port = cluster.selfMember().address().port().isDefined() ? (int) cluster.selfMember().address().port().get() : 0;
        return host + ":" + port;
    }
}
