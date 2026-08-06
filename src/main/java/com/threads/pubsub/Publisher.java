package com.threads.pubsub;

class Publisher implements Runnable {

    private final Topic topic;

    public Publisher(Topic topic) {
        this.topic = topic;
    }

    @Override
    public void run() {
        try {
            for (int i = 1; i <= 5; i++) {
                Message msg = new Message("Message " + i);
                topic.publish(msg);
                System.out.println("Published: " + msg.getContent());
                Thread.sleep(500);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}