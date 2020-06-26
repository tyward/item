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
package edu.columbia.tjw.item.optimize;

import edu.columbia.tjw.item.algo.DoubleVector;
import edu.columbia.tjw.item.util.thread.GeneralTask;
import edu.columbia.tjw.item.util.thread.GeneralThreadPool;

import java.util.ArrayList;
import java.util.List;

/**
 * @author tyler
 */
public abstract class ThreadedMultivariateFunction implements MultivariateOptimizationFunction
{
    private static final GeneralThreadPool POOL = GeneralThreadPool.singleton();
    private final int _blockSize;
    private final boolean _useThreading;
    private final Object _prepLock = new Object();

    public ThreadedMultivariateFunction(final int blockSize_, final boolean useThreading_)
    {
        _blockSize = blockSize_;
        _useThreading = useThreading_;
    }

    @Override
    public abstract int dimension();

    public abstract int resultSize(final int start_, final int end_);


    private <W> List<W> executeTasks(final List<? extends GeneralTask<W>> tasks_)
    {
        final List<W> output = new ArrayList<>(tasks_.size());

        for (final GeneralTask<W> next : tasks_)
        {
            if (_useThreading)
            {
                POOL.execute(next);
            }
            else
            {
                next.run();
            }
        }

        for (final GeneralTask<W> next : tasks_)
        {
            final W res = next.waitForCompletion();
            output.add(res);
        }

        return output;
    }


    @Override
    public abstract int numRows();

    protected abstract void prepare(final DoubleVector input_);


}
