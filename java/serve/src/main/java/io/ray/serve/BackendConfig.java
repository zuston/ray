package io.ray.serve;

import java.io.Serializable;

/**
 * BackendConfig.
 */
public class BackendConfig implements Serializable {
  
  private static final long serialVersionUID = 244486384449779141L;

  private int numReplicas;

  private int maxConcurrentQueries;

  private Object userConfig;

  private long experimentalGracefulShutdownWaitLoopS = 2;

  private long experimentalGracefulShutdownTimeoutS = 20;

  public int getNumReplicas() {
    return numReplicas;
  }

  public void setNumReplicas(int numReplicas) {
    this.numReplicas = numReplicas;
  }

  public int getMaxConcurrentQueries() {
    return maxConcurrentQueries;
  }

  public void setMaxConcurrentQueries(int maxConcurrentQueries) {
    this.maxConcurrentQueries = maxConcurrentQueries;
  }

  public Object getUserConfig() {
    return userConfig;
  }

  public void setUserConfig(Object userConfig) {
    this.userConfig = userConfig;
  }

  public long getExperimentalGracefulShutdownWaitLoopS() {
    return experimentalGracefulShutdownWaitLoopS;
  }

  public void setExperimentalGracefulShutdownWaitLoopS(long experimentalGracefulShutdownWaitLoopS) {
    this.experimentalGracefulShutdownWaitLoopS = experimentalGracefulShutdownWaitLoopS;
  }

  public long getExperimentalGracefulShutdownTimeoutS() {
    return experimentalGracefulShutdownTimeoutS;
  }

  public void setExperimentalGracefulShutdownTimeoutS(long experimentalGracefulShutdownTimeoutS) {
    this.experimentalGracefulShutdownTimeoutS = experimentalGracefulShutdownTimeoutS;
  }


}
