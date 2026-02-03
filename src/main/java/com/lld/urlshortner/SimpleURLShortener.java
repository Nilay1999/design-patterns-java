package com.lld.urlshortner;

import java.util.HashMap;
import java.util.Map;

class SimpleURLShortener implements URLShortener {
    private Map<String, String> shortToLong = new HashMap<>();
    private Map<String, String> longToShort = new HashMap<>();
    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final String DOMAIN = "short.ly/";
    private long counter = 1000000;

    public String shorten(String longUrl) {
        if (longToShort.containsKey(longUrl)) {
            return DOMAIN + longToShort.get(longUrl);
        }

        String shortCode = encodeBase62(counter++);
        shortToLong.put(shortCode, longUrl);
        longToShort.put(longUrl, shortCode);

        return DOMAIN + shortCode;
    }

    public String getOriginal(String shortUrl) {
        String shortCode = shortUrl.replace(DOMAIN, "");
        return shortToLong.getOrDefault(shortCode, "URL not found");
    }

    private String encodeBase62(long num) {
        StringBuilder sb = new StringBuilder();
        while (num > 0) {
            sb.insert(0, BASE62.charAt((int) (num % 62)));
            num /= 62;
        }
        return sb.toString();
    }
}