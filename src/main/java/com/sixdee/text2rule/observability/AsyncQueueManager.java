package com.sixdee.text2rule.observability;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class AsyncQueueManager {
    private static volatile AsyncQueueManager instance;
    private final BlockingQueue<ObservabilityEvent> queue;

    private AsyncQueueManager() {
        this.queue = new LinkedBlockingQueue<>();
    }

    public static AsyncQueueManager getInstance() {
        if (instance == null) {
            synchronized (AsyncQueueManager.class) {
                if (instance == null) {
                    instance = new AsyncQueueManager();
                }
            }
        }
        return instance;
    }

    public boolean offer(ObservabilityEvent event) {
        return queue.offer(event);
    }

    public ObservabilityEvent take() throws InterruptedException {
        return queue.take();
    }
}
