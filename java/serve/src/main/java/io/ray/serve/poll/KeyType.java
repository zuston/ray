package io.ray.serve.poll;

import java.io.Serializable;

/**
 * Key type of long poll.
 */
public class KeyType implements Serializable {

  private static final long serialVersionUID = -8838552786234630401L;

  private LongPollNamespace longPollNamespace;

  private String key;

  private int hash;

  public KeyType(LongPollNamespace longPollNamespace, String key) {
    this.longPollNamespace = longPollNamespace;
    this.key = key;
  }

  public LongPollNamespace getLongPollNamespace() {
    return longPollNamespace;
  }

  public void setLongPollNamespace(LongPollNamespace longPollNamespace) {
    this.longPollNamespace = longPollNamespace;
  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  @Override
  public int hashCode() {
    int h = hash;
    if (h == 0) {
      h = 31 * h + longPollNamespace.hashCode();
      h = 31 * h + key.hashCode();
    }
    return h;
  }

}
