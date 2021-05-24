package io.ray.serve.api;

import io.ray.serve.RayServeException;
import io.ray.serve.ReplicaContext;

public class Serve {

  public static ReplicaContext INTERNAL_REPLICA_CONTEXT;

  public static void setInternalReplicaContext(String backendTag, String replicaTag,
      String controllerName, Object servableObject) {
    INTERNAL_REPLICA_CONTEXT =
        new ReplicaContext(backendTag, replicaTag, controllerName, servableObject);
  }

  public static ReplicaContext getReplicaContext() {
    if (INTERNAL_REPLICA_CONTEXT == null) {
      throw new RayServeException(
          "`Serve.getReplicaContext()` may only be called from within a Ray Serve backend.");
    }
    return INTERNAL_REPLICA_CONTEXT;
  }

}
