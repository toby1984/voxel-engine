package de.codesourcery.voxelengine.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.log4j.Logger;

import com.badlogic.gdx.utils.Disposable;

public class TaskScheduler implements Disposable
{
    private static final Logger LOG = Logger.getLogger(TaskScheduler.class);

    private final Object RENDER_QUEUE_LOCK = new Object();
    private LinkedBlockingQueue<Task> renderQueue=new LinkedBlockingQueue<>();

    private final Object QUEUE_LOCK = new Object();
    private LinkedList<Task> hiPrioQueue=new LinkedList<>();
    private LinkedList<Task> loPrioQueue=new LinkedList<>();

    private final List<WorkerThread> workerThreads = new ArrayList<>();

    public static final class QueueEntry 
    {
        public QueueEntry next;
        public Task task;
    }

    public static enum Prio 
    {
        RENDER,
        HI,
        LO
    }

    public static abstract class Task 
    {
        public final Prio priority;

        public Task(Prio prio) {
            this.priority = prio;
        }

        /**
         * 
         * @return true of task is done and should be discarded
         */
        public abstract boolean perform();
    }

    protected final class WorkerThread extends Thread 
    {
        private final LinkedList<Task> queue;
        public volatile boolean terminate;
        private final CountDownLatch stopLatch = new CountDownLatch(1);

        public WorkerThread(LinkedList<Task> queue , ThreadGroup group, String name) {
            super(group, name);
            this.queue = queue;
            setDaemon(true);
        }

        public void dispose() throws InterruptedException 
        {
            LOG.info("dispose(): Asking thread "+this+" to stop...");
            terminate = true;
            synchronized( QUEUE_LOCK ) {
                QUEUE_LOCK.notifyAll();
            }
            LOG.info("dispose(): Waiting for thread "+this+" to stop...");
            stopLatch.await();
            LOG.info("dispose(): Finished waiting for thread "+this);
        }

        @Override
        public void run() 
        {
            LOG.info("run(): Thread started");
            try 
            {
                while ( ! terminate ) 
                {
                    Task task;
                    synchronized( QUEUE_LOCK ) 
                    {
                        task = queue.poll();
                        if ( task == null ) {
                            try {
                                QUEUE_LOCK.wait();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            continue;
                        }
                    }
                    boolean remove = true;
                    try {
                        remove = task.perform();
                    } 
                    catch(Exception e) {
                        LOG.error("run(): Task "+task+" failed: " +e.getMessage(),e);
                    } 
                    finally 
                    {
                        if ( remove ) 
                        {
                            synchronized( QUEUE_LOCK ) 
                            {
                                queue.remove( task );
                            }
                        }
                    }
                }
            } 
            finally 
            {
                LOG.info("run(): Thread terminated.");
                stopLatch.countDown();
            }
        }        
    }

    public TaskScheduler() 
    {
        final ThreadGroup group = new ThreadGroup( Thread.currentThread().getThreadGroup() , "queue-workers" );
        workerThreads.addAll( Arrays.asList( 
                new WorkerThread(hiPrioQueue , group,"queue-worker-hi-1"), 
                new WorkerThread(hiPrioQueue , group,"queue-worker-hi-2"),
                new WorkerThread(loPrioQueue , group,"queue-worker-lo-1") 
                ));

        workerThreads.forEach( Thread::start );
    }

    public void add(Task task) 
    {
        task.perform();
//        switch( task.priority ) 
//        {
//            case HI:
//                synchronized( QUEUE_LOCK ) 
//                {
//                    hiPrioQueue.add( task );
//                    QUEUE_LOCK.notifyAll();
//                }
//                break;
//            case LO:
//                synchronized( QUEUE_LOCK ) {
//                    loPrioQueue.add( task );
//                    QUEUE_LOCK.notifyAll();
//                }                
//                break;
//            case RENDER:
//                synchronized( RENDER_QUEUE_LOCK ) {
//                    renderQueue.add( task );
//                }
//                break;
//            default:
//                break;
//        }
    }

//    public void add(List<Task> tasks) 
//    {
//        final int len = tasks.size();
//        final ArrayList<Task> hi= new ArrayList<>(len);
//        final ArrayList<Task> lo = new ArrayList<>(len);
//        final ArrayList<Task> render = new ArrayList<>(len);
//
//        for ( Task task : tasks ) 
//        {
//            switch( task.priority ) 
//            {
//                case HI:
//                    hi.add( task );
//                    break;
//                case LO:
//                    lo.add( task );
//                    break;
//                case RENDER:
//                    render.add( task );
//            }
//        }
//        if ( ! hi.isEmpty() || ! lo.isEmpty() ) 
//        {
//            synchronized( QUEUE_LOCK ) 
//            {
//                hiPrioQueue.addAll( hi );
//                loPrioQueue.addAll( lo );
//                QUEUE_LOCK.notifyAll();
//            }              
//        }
//
//        if ( ! render.isEmpty() ) 
//        {
//            synchronized( RENDER_QUEUE_LOCK ) 
//            {
//                renderQueue.addAll( render );
//            }
//        }
//    }

    public void render() 
    {
        synchronized( RENDER_QUEUE_LOCK ) 
        {
            if ( ! renderQueue.isEmpty() ) 
            {
                for ( Iterator<Task> it = renderQueue.iterator() ; it.hasNext() ; ) 
                {
                    final Task task = it.next();
                    if ( ! task.perform() ) 
                    {
                        it.remove();
                    }
                }
            }
        }
    }

    @Override
    public void dispose() 
    {
        LOG.info("dispose(): Called");
        for ( WorkerThread t : workerThreads ) 
        {
            try {
                t.dispose();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        LOG.info("dispose(): All worker threads stopped");
    }
}