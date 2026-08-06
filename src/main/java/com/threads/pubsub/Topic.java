package com.threads.pubsub;

import java.util.concurrent.LinkedBlockingQueue;

class Topic {
    private final LinkedBlockingQueue<Message> queue = new LinkedBlockingQueue<>();

    public void publish(Message message) throws InterruptedException {
        queue.put(message);
    }

    public Message consume() throws InterruptedException {
        return queue.take();
    }
}