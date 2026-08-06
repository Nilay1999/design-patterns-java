package com.threads.pubsub;

class Subscriber implements Runnable {

    private final Topic topic;
    private final String name;

    public Subscriber(Topic topic, String name) {
        this.topic = topic;
        this.name = name;
    }

    @Override
    public void run() {
        try {
            while (true) {
                Message msg = topic.consume();
                System.out.println(name + " received: " + msg.getContent());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}