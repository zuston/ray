package io.ray.serve.poll;

import io.ray.api.BaseActorHandle;
import io.ray.api.ObjectRef;
import io.ray.api.PyActorHandle;
import io.ray.api.function.PyActorMethod;
import io.ray.runtime.exception.RayActorException;
import io.ray.runtime.exception.RayTaskException;
import io.ray.serve.Constants;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LongPollClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(LongPollClient.class);

  private BaseActorHandle hostActor;

  private Map<KeyType, Consumer<Object>> keyListeners;

  private Map<KeyType, Integer> snapshotIds;

  private Map<KeyType, Object> objectSnapshots;

  private ObjectRef<Object> currentRef;

  private Thread pollThread;

  public LongPollClient(BaseActorHandle hostActor, Map<KeyType, Consumer<Object>> keyListeners) {
    this.hostActor = hostActor;
    this.keyListeners = keyListeners;
    reset();
    this.pollThread = new Thread(() -> {
      while (true) {
        pollNext();
      }
    }, "backend-poll-thread");
    this.pollThread.start();
  }

  private void reset() {
    snapshotIds = new ConcurrentHashMap<>();
    objectSnapshots = new ConcurrentHashMap<>();
    currentRef = null;
  }

  private void pollNext() {
    currentRef = ((PyActorHandle) hostActor)
        .task(PyActorMethod.of(Constants.CONTROLLER_LISTEN_FOR_CHANGE_METHOD), snapshotIds)
        .remote();
    Object updates = currentRef.get();
    processUpdate(updates);

  }

  @SuppressWarnings("unchecked")
  private void processUpdate(Object updates) {
    if (updates instanceof RayActorException) {
      LOGGER.debug("LongPollClient failed to connect to host. Shutting down.");
      // TODO exist async thread.
      return;
    }

    if (updates instanceof RayTaskException) {
      LOGGER.error("LongPollHost errored", (RayTaskException) updates);
      return;
    }

    Map<KeyType, UpdatedObject> updateObjects = (Map<KeyType, UpdatedObject>) updates;

    LOGGER.debug("LongPollClient received updates for keys: {}", updateObjects.keySet());
    
    for (Map.Entry<KeyType, UpdatedObject> entry : updateObjects.entrySet()) {
      objectSnapshots.put(entry.getKey(), entry.getValue().getObjectSnapshot());
      snapshotIds.put(entry.getKey(), entry.getValue().getSnapshotId());
      Consumer<Object> consumer = keyListeners.get(entry.getKey());
      consumer.accept(entry.getValue().getObjectSnapshot());
    }

  }

}
