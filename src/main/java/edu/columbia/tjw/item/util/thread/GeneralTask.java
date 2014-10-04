/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.columbia.tjw.item.util.thread;

/**
 *
 * @author tyler
 */
public abstract class GeneralTask<V> implements Runnable
{
    private Throwable _exception;
    private V _result;
    private boolean _isDone;

    public GeneralTask()
    {
        _exception = null;
        _result = null;
        _isDone = false;
    }

    public synchronized boolean isDone()
    {
        return _isDone;
    }

    public synchronized V waitForCompletion()
    {
        while (!_isDone)
        {
            try
            {
                this.wait();
            } catch (final InterruptedException e)
            {
                throw new RuntimeException(e);
            }
        }

        if (null != _exception)
        {
            throw new RuntimeException(_exception);
        }

        return _result;
    }

    @Override
    public void run()
    {
        V result = null;
        Throwable t = null;

        try
        {
            result = subRun();
            t = null;

        } catch (final Throwable t_)
        {
            t = t_;
            result = null;
        } finally
        {
            synchronized (this)
            {
                this._result = result;
                this._exception = t;
                this._isDone = true;

                this.notifyAll();
            }
        }
    }

    protected abstract V subRun();
}
