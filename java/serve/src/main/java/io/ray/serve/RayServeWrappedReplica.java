package io.ray.serve;

import com.google.common.base.Preconditions;
import io.ray.api.BaseActorHandle;
import io.ray.api.Ray;
import io.ray.serve.api.Serve;
import io.ray.serve.util.ReflectUtil;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

/**
 * RayServeWrappedReplica.
 */
public class RayServeWrappedReplica {
  
  private RayServeReplica backend;

  @SuppressWarnings("rawtypes")
  public RayServeWrappedReplica(String backendTag, String replicaTag, String backendDef,
      Object[] initArgs, BackendConfig backendConfig, String controllerName)
      throws ClassNotFoundException, NoSuchMethodException, SecurityException,
      InstantiationException, IllegalAccessException, IllegalArgumentException,
      InvocationTargetException {

    // Instantiate the object defined by backendDef. Not support Java function now.
    Class backendClass = Class.forName(backendDef);
    Object callable =
        ReflectUtil.getConstructor(backendClass, initArgs).newInstance(initArgs);

    Preconditions.checkArgument(StringUtils.isNotBlank(controllerName),
        "Must provide a valid controllerName");
    Optional<BaseActorHandle> optional = Ray.getActor(controllerName);
    Preconditions.checkState(optional.isPresent(), "Controller does not exist");

    // Set the controller name so that Serve.connect() in the user's backend code will connect to
    // the instance that this backend is running in.
    Serve.setInternalReplicaContext(backendTag, replicaTag, controllerName, callable);

    backend = new RayServeReplica(callable, backendConfig, optional.get());

  }

  public void handle_request(RequestMetadata requestMetadata, List<Object> requestArgs) {
    backend.handle_request(new Query(requestArgs, requestMetadata));
  }

  public void ready() {
    return;
  }

  public void drain_pending_queries() throws InterruptedException {
    backend.drain_pending_queries();
  }

}
