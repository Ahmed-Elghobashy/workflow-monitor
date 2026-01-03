package com.github.workflows.ui;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simple console spinner that renders on stderr so stdout can remain parseable.
 */
public class ConsoleSpinner {
    private static final char[] FRAMES = {'|', '/', '-', '\\'};
    private final String message;
    private final long intervalMillis;
    private final AtomicBoolean spinning = new AtomicBoolean(false);
    private Thread worker;

    public ConsoleSpinner(String message) {
        this(message, 150);
    }

    public ConsoleSpinner(String message, long intervalMillis) {
        this.message = message;
        this.intervalMillis = intervalMillis;
    }

    public void start() {
        if (spinning.compareAndSet(false, true)) {
            worker = new Thread(this::runSpinner, "console-spinner");
            worker.setDaemon(true);
            worker.start();
        }
    }

    public void stop() {
        stop(null);
    }

    public void stop(String finalMessage) {
        pauseSpinner();
        if (finalMessage != null && !finalMessage.isBlank()) {
            System.err.println(finalMessage);
        }
    }

    /**
     * Execute a block while the spinner is hidden to avoid overlapping output.
     */
    public void runBlocking(Runnable action) {
        boolean restart = pauseSpinner();
        try {
            action.run();
        } finally {
            if (restart) {
                start();
            }
        }
    }

    private void runSpinner() {
        int index = 0;
        while (spinning.get()) {
            System.err.print("\r" + message + " " + FRAMES[index]);
            System.err.flush();
            index = (index + 1) % FRAMES.length;
            try {
                Thread.sleep(intervalMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        clearLine();
    }

    private boolean pauseSpinner() {
        if (spinning.compareAndSet(true, false)) {
            stopWorker();
            clearLine();
            return true;
        }
        return false;
    }

    private void stopWorker() {
        Thread thread = worker;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void clearLine() {
        int length = message.length() + 2;
        StringBuilder builder = new StringBuilder(length + 2);
        builder.append('\r');
        for (int i = 0; i < length; i++) {
            builder.append(' ');
        }
        builder.append('\r');
        System.err.print(builder);
        System.err.flush();
    }
}
