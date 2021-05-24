package io.ray.serve;

import io.ray.api.BaseActorHandle;
import io.ray.api.Ray;
import io.ray.runtime.exception.RayTaskException;
import io.ray.runtime.metric.Count;
import io.ray.runtime.metric.Gauge;
import io.ray.runtime.metric.Histogram;
import io.ray.runtime.metric.TagKey;
import io.ray.serve.api.Serve;
import io.ray.serve.poll.KeyType;
import io.ray.serve.poll.LongPollClient;
import io.ray.serve.poll.LongPollNamespace;
import io.ray.serve.util.LogUtil;
import io.ray.serve.util.ReflectUtil;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RayServeReplica.
 */
public class RayServeReplica {

  private static final Logger LOGGER = LoggerFactory.getLogger(RayServeReplica.class);

  private String backendTag;

  private String replicaTag;

  private BackendConfig config;

  private AtomicInteger numOngoingRequests = new AtomicInteger();

  private Object callable;

  private Count requestCounter;

  private Count errorCounter;

  private Count restartCounter;

  private Histogram processingLatencyTracker;

  private Gauge numProcessingItems;

  @SuppressWarnings("unused")
  private LongPollClient longPollClient;

  public RayServeReplica(Object callable, BackendConfig backendConfig, BaseActorHandle actorHandle)
      throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    this.backendTag = Serve.getReplicaContext().getBackendTag();
    this.replicaTag = Serve.getReplicaContext().getReplicaTag();
    this.callable = callable;
    this.config = backendConfig;
    this.reconfigure(backendConfig.getUserConfig());

    Map<KeyType, Consumer<Object>> keyListeners = new HashMap<>();
    keyListeners.put(new KeyType(LongPollNamespace.BACKEND_CONFIGS, backendTag),
        newConfig -> {
          try {
            _update_backend_configs(newConfig);
          } catch (IllegalAccessException | IllegalArgumentException
              | InvocationTargetException e) {
            // TODO handle error.
          }
        });
    this.longPollClient = new LongPollClient(actorHandle, keyListeners);

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

    LogUtil.setLayout(backendTag, replicaTag);

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

    try {
      return ReflectUtil.getMethod(callable.getClass(), methodName,
          query.getArgs() == null ? null : query.getArgs().toArray());
    } catch (NoSuchMethodException e) {
      throw new RayServeException(LogUtil.format(
          "Backend doesn't have method {} which is specified in the request. The available methods are {}",
          methodName, ReflectUtil.getMethodStrings(callable.getClass())));
    }

  }

  public void drain_pending_queries() throws InterruptedException {
    while (true) {
      Thread.sleep(this.config.getExperimentalGracefulShutdownWaitLoopS() * 1000);
      if (this.numOngoingRequests.get() == 0) {
        break;
      } else {
        LOGGER.debug(
            "Waiting for an additional {}s to shut down because there are {} ongoing requests.",
            this.config.getExperimentalGracefulShutdownWaitLoopS(), this.numOngoingRequests.get());
      }
    }
    Ray.exitActor();
  }

  private void reconfigure(Object userConfig)
      throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    try {
      Method reconfigureMethod = ReflectUtil.getMethod(callable.getClass(),
          Constants.BACKEND_RECONFIGURE_METHOD, userConfig);

      reconfigureMethod.invoke(callable, userConfig);
    } catch (NoSuchMethodException e) {
      throw new RayServeException(
          LogUtil.format("user_config specified but backend {}  missing {} method", backendTag,
              Constants.BACKEND_RECONFIGURE_METHOD));
    }
  }

  private void _update_backend_configs(Object newConfig)
      throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    this.reconfigure(((BackendConfig) newConfig).getUserConfig());
  }

}
