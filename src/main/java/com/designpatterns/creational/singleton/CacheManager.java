package com.designpatterns.creational.singleton;

import java.util.HashMap;
import java.util.Map;

public class CacheManager {
  private static CacheManager instance;
  private final Map<String, Object> cache = new HashMap<>();

  private CacheManager() {
  }

  public static synchronized CacheManager getInstance() {
    if (instance == null) {
      instance = new CacheManager();
    }
    return instance;
  }

  public void set(String key, Object value) {
    this.cache.put(key, value);
  }

  public Object get(String key) {
    return this.cache.get(key);
  }
}
