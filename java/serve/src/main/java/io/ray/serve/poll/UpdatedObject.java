package io.ray.serve.poll;

import java.io.Serializable;

public class UpdatedObject implements Serializable {

  private static final long serialVersionUID = 6245682414826079438L;

  private Object objectSnapshot;

  private int snapshotId;

  public Object getObjectSnapshot() {
    return objectSnapshot;
  }

  public void setObjectSnapshot(Object objectSnapshot) {
    this.objectSnapshot = objectSnapshot;
  }

  public int getSnapshotId() {
    return snapshotId;
  }

  public void setSnapshotId(int snapshotId) {
    this.snapshotId = snapshotId;
  }

}
