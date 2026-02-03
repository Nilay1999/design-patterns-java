package com.lld.urlshortner;

public interface URLShortener {
    public String shorten(String longUrl);

    public String getOriginal(String shortUrl);
}
