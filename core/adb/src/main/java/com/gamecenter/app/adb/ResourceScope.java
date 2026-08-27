package com.gamecenter.app.adb;

import java.io.Closeable;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.LinkedHashSet;
import java.util.Set;

/** Cancellation closes actual blocking handles, not just a Future or an obsolete UI callback. */
final class ResourceScope implements Closeable {
    private final Set<Closeable> resources = new LinkedHashSet<>();
    private boolean closed;
    synchronized <T extends Closeable> T own(T resource) throws IOException {
        if (closed) { resource.close(); throw new InterruptedIOException("任务已取消"); }
        resources.add(resource); return resource;
    }
    synchronized void release(Closeable resource) { resources.remove(resource); }
    synchronized void check() throws IOException { if (closed || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("任务已取消"); }
    synchronized boolean isClosed() { return closed; }
    @Override public void close() {
        Closeable[] items;
        synchronized (this) {
            if (closed) return;
            closed = true; items = resources.toArray(new Closeable[0]); resources.clear();
        }
        for (int i = items.length - 1; i >= 0; i--) try { items[i].close(); } catch (Exception ignored) { }
    }
}
