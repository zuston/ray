package io.ray.serve;

import io.ray.api.BaseActorHandle;
import io.ray.runtime.exception.RayTaskException;
import io.ray.runtime.metric.Count;
import io.ray.runtime.metric.Gauge;
import io.ray.runtime.metric.Histogram;
import io.ray.runtime.metric.TagKey;
import io.ray.serve.api.Serve;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RayServeReplica.
 */
public class RayServeReplica {

  private static final Logger LOGGER = LoggerFactory.getLogger(RayServeReplica.class);

  private String backendTag;

  private String replicaTag;

  private AtomicInteger numOngoingRequests = new AtomicInteger();

  private Object callable;

  private Count requestCounter;

  private Count errorCounter;

  private Count restartCounter;

  private Histogram processingLatencyTracker;

  private Gauge numProcessingItems;

  public RayServeReplica(Object callable, BackendConfig backendConfig, BaseActorHandle actorHandle)
      throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    this.backendTag = Serve.getReplicaContext().getBackendTag();
    this.replicaTag = Serve.getReplicaContext().getReplicaTag();
    this.callable = callable;
    this.reconfigure(backendConfig.getUserConfig());

    Map<TagKey, String> tags = new HashMap<>();
    tags.put(new TagKey("backend"), backendTag);
    this.requestCounter = new Count("serve_backend_request_counter",
        "The number of queries that have been processed in this replica.", "", tags);
    this.errorCounter = new Count("serve_backend_error_counter",
        "The number of exceptions that have occurred in the backend.", "", tags);

    tags = new HashMap<>();
    tags.put(new TagKey("backend"), backendTag);
    tags.put(new TagKey("replica"), replicaTag);
    this.restartCounter = new Count("serve_backend_replica_starts",
        "The number of times this replica has been restarted due to failure.", "", tags);
    this.processingLatencyTracker = new Histogram("serve_backend_processing_latency_ms",
        "The latency for queries to be processed.", "", Constants.DEFAULT_LATENCY_BUCKET_MS, tags);
    this.numProcessingItems = new Gauge("serve_replica_processing_queries",
        "The current number of queries being processed.", "", tags);

    this.restartCounter.inc(1.0);

    // TODO loop for _update_backend_configs
    // TODO logger format

  }

  public Object handle_request(Query request) {
    long startTime = System.currentTimeMillis();
    LOGGER.debug("Replica {} received request {}", this.replicaTag,
        request.getMetadata().getRequestId());

    this.numProcessingItems.update(numOngoingRequests.incrementAndGet());
    Object result = invokeSingle(request);
    this.numOngoingRequests.decrementAndGet();
    long requestTimeMs = System.currentTimeMillis() - startTime;
    LOGGER.debug("Replica {} finished request {} in {}ms", this.replicaTag,
        request.getMetadata().getRequestId(), requestTimeMs);

    return result;
  }

  private Object invokeSingle(Query requestItem) {
    LOGGER.debug("Replica {} started executing request {}", this.replicaTag,
        requestItem.getMetadata().getRequestId());

    long start = System.currentTimeMillis();
    Method methodToCall = null;
    Object result = null;
    try {
      methodToCall = getRunnerMethod(requestItem);
      result = methodToCall.invoke(callable, requestItem.getArgs());

      this.requestCounter.inc(1.0);
    } catch (Throwable e) {
      this.errorCounter.inc(1.0);
      throw new RayTaskException(methodToCall == null ? "unknown" : methodToCall.getName(), e);
    }

    long latencyMs = System.currentTimeMillis() - start;
    this.processingLatencyTracker.update(latencyMs);

    return result;
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
