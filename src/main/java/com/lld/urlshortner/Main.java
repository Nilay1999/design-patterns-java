package com.lld.urlshortner;

public class Main {
    public static void main(String[] args) {
        // Test basic version
        System.out.println("=== Basic URL Shortener ===");
        URLShortener shortener = new SimpleURLShortener();

        String url1 = "https://www.example.com/very/long/path";
        String short1 = shortener.shorten(url1);
        System.out.println("Shortened: " + short1);
        System.out.println("Original: " + shortener.getOriginal(short1));
    }
}
