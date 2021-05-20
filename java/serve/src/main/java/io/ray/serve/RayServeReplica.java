package io.ray.serve;

import io.ray.api.BaseActorHandle;
import io.ray.runtime.exception.RayTaskException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RayServeReplica.
 */
public class RayServeReplica {

  private static final Logger LOGGER = LoggerFactory.getLogger(RayServeReplica.class);

  private String backendTag;

  private Object callable;

  public RayServeReplica(Object callable, BackendConfig backendConfig, BaseActorHandle actorHandle)
      throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    this.callable = callable;
    this.reconfigure(backendConfig.getUserConfig());
    // TODO metrics
    // TODO loop for _update_backend_configs
    // TODO logger

  }

  public Object handle_request(Query request) {
    long startTime = System.currentTimeMillis();
    // TODO log
    // TODO metrics
    Object result = invokeSingle(request);
    long requestTimeMs = System.currentTimeMillis() - startTime;
    // TODO log

    return result;
  }

  private Object invokeSingle(Query requestItem) {
    // TODO log
    // long start = System.currentTimeMillis();
    Method methodToCall = null;
    try {
      methodToCall = getRunnerMethod(requestItem);
      return methodToCall.invoke(callable, requestItem.getArgs());
    } catch (Throwable e) {
      throw new RayTaskException(methodToCall == null ? "unknown" : methodToCall.getName(), e);
    }
  }

  private Method getRunnerMethod(Query query) {
    String methodName = query.getMetadata().getCallMethod();

    Class[] parameterTypes = null;
    if (query.getArgs() != null && query.getArgs().size() > 0) {
      parameterTypes = new Class[query.getArgs().size()];
      for (int i = 0; i < query.getArgs().size(); i++) {
        parameterTypes[i] = query.getArgs().get(i).getClass();
      }
    } // TODO Extract to util.

    try {
      return callable.getClass().getMethod(methodName, parameterTypes);
    } catch (NoSuchMethodException e) {
      throw new RayServeException(
          "Backend doesn't have method " + methodName
              + "which is specified in the request. The available methods are "
              + callable.getClass().getMethods()); // TODO string format.
    }

  }

  public void drain_pending_queries() {

  }

  private void reconfigure(Object userConfig)
      throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    try {
      Method reconfigureMethod =
          callable.getClass().getMethod(Constants.BACKEND_RECONFIGURE_METHOD,
              userConfig.getClass()); // TODO Extract to util.

      reconfigureMethod.invoke(callable, userConfig);
    } catch (NoSuchMethodException e) {
      throw new RayServeException("user_config specified but backend " + backendTag + " missing "
          + Constants.BACKEND_RECONFIGURE_METHOD + " method"); // TODO string format.
    }
  }

  private void _update_backend_configs(BackendConfig newConfig)
      throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    this.reconfigure(newConfig.getUserConfig());
  }

}
