package com.moveinsync.mobilitycopilot.workflow.investigation.executor;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Process-wide admission: concurrent runs cannot each allocate unbounded thread pools. */
@Component
public final class BoundedInvestigationExecutor implements AutoCloseable {
    private final ThreadPoolExecutor executor;
    public BoundedInvestigationExecutor(@Value("${mobility.workflow.threads:4}") int threads,
            @Value("${mobility.workflow.queue-capacity:32}") int queueCapacity) {
        if(threads<1||queueCapacity<1)throw new IllegalArgumentException("Invalid workflow capacity");
        executor=new ThreadPoolExecutor(threads,threads,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(queueCapacity),
                r->{Thread t=new Thread(r,"governed-investigation");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    }
    public <T> Future<T> submit(Callable<T> work){return executor.submit(work);}
    public int queued(){return executor.getQueue().size();}
    @Override @PreDestroy public void close(){executor.shutdownNow();}
}
