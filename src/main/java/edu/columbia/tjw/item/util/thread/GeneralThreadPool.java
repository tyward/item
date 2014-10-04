/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.columbia.tjw.item.util.thread;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author tyler
 */
public class GeneralThreadPool extends ThreadPoolExecutor
{
    private static final int NUM_PROCESSORS = Runtime.getRuntime().availableProcessors();
    private static final GeneralThreadPool SINGLETON = new GeneralThreadPool();

    public static GeneralThreadPool singleton()
    {
        return SINGLETON;
    }

    private GeneralThreadPool()
    {
        super(NUM_PROCESSORS, 2 * NUM_PROCESSORS, 500, TimeUnit.SECONDS, new LinkedBlockingDeque<Runnable>());

        this.setThreadFactory(new GeneralFactory());

    }

    private static final class GeneralFactory implements ThreadFactory
    {

        @Override
        public Thread newThread(Runnable r)
        {
            final Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        }

    }

}
