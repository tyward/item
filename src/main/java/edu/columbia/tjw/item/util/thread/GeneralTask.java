/*
 * Copyright 2014 Tyler Ward.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * This code is part of the reference implementation of http://arxiv.org/abs/1409.6075
 * 
 * This is provided as an example to help in the understanding of the ITEM model system.
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
