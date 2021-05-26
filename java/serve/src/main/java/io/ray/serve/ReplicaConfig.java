package io.ray.serve;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * ReplicaConfig.
 */
public class ReplicaConfig implements Serializable {

  private static final long serialVersionUID = -1442657824045704226L;

  private String backendDef;

  private Object[] initArgs;

  private Map<String, Object> rayActorOptions;

  private Map<String, Object> resource;

  public ReplicaConfig(String backendDef, Object[] initArgs, Map<String, Object> rayActorOptions) {
    this.backendDef = backendDef;
    this.initArgs = initArgs;
    this.rayActorOptions = rayActorOptions;
    this.resource = new HashMap<>();
    this.validate();
  }

  private void validate() {
    if (rayActorOptions.containsKey("placement_group")) {
      throw new IllegalArgumentException(
          "Providing placement_group for backend actors is not currently supported.");
    }
    if (rayActorOptions.containsKey("lifetime")) {
      throw new IllegalArgumentException("Specifying lifetime in init_args is not allowed.");
    }
    if (rayActorOptions.containsKey("name")) {
      throw new IllegalArgumentException("Specifying name in init_args is not allowed.");
    }
    if (rayActorOptions.containsKey("max_restarts")) {
      throw new IllegalArgumentException("Specifying max_restarts in init_args is not allowed.");
    }

  }

  public String getBackendDef() {
    return backendDef;
  }

  public void setBackendDef(String backendDef) {
    this.backendDef = backendDef;
  }

  public Object[] getInitArgs() {
    return initArgs;
  }

  public void setInitArgs(Object[] initArgs) {
    this.initArgs = initArgs;
  }

  public Map<String, Object> getRayActorOptions() {
    return rayActorOptions;
  }

  public void setRayActorOptions(Map<String, Object> rayActorOptions) {
    this.rayActorOptions = rayActorOptions;
  }

  public Map<String, Object> getResource() {
    return resource;
  }

  public void setResource(Map<String, Object> resource) {
    this.resource = resource;
  }

}
