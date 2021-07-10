package com.hlf.rpc.common;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 只release一次的信号量
 */
public class SemaphoreReleaseOnlyOnce {

    private final AtomicBoolean released = new AtomicBoolean(false);
    private final Semaphore semaphore;

    public SemaphoreReleaseOnlyOnce(Semaphore semaphore) {
        this.semaphore = semaphore;
    }

    // release
    public void release() {
        if (semaphore != null) {
            if (this.released.compareAndSet(false, true)) {
                this.semaphore.release();
            }
        }
    }


    public Semaphore getSemaphore() {
        return this.semaphore;
    }

}
