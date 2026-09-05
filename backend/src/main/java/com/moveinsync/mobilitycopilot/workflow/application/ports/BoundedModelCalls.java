package com.moveinsync.mobilitycopilot.workflow.application.ports;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;

/** Shared optional-provider bulkhead. Exhaustion/timeouts route back to deterministic agents. */
public final class BoundedModelCalls {
    private static final ThreadPoolExecutor POOL = new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(8),r->{Thread t=new Thread(r,"optional-model");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private BoundedModelCalls(){}
    public static LanguageModelPort.ModelResponse complete(LanguageModelPort model,LanguageModelPort.ModelRequest request) {
        if(request.prompt()==null||request.prompt().length()>24000||request.evidence().size()>64)
            throw new IllegalArgumentException("Optional model context exceeds compact evidence limit");
        long remaining=Duration.between(Instant.now(),request.context().deadline()).toMillis();
        long allowance=Math.min(remaining,request.context().budget().investigationTimeout().toMillis());
        if(allowance<=0)throw new IllegalStateException("Model budget expired");
        Future<LanguageModelPort.ModelResponse> task=POOL.submit(()->model.complete(request));
        try{return task.get(allowance,TimeUnit.MILLISECONDS);}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Model interrupted",e);}
        catch(ExecutionException|TimeoutException e){throw new IllegalStateException("Optional model unavailable",e);}
        finally{if(!task.isDone())task.cancel(true);POOL.purge();}
    }
}
