/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.columbia.tjw.item.optimize;

import edu.columbia.tjw.item.util.thread.GeneralTask;
import edu.columbia.tjw.item.util.thread.GeneralThreadPool;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author tyler
 */
public abstract class ThreadedMultivariateFunction implements MultivariateFunction
{
    private static final boolean USE_THREADS = true;
    private static final GeneralThreadPool POOL = GeneralThreadPool.singleton();
    private static final int DEFAULT_BLOCK_SIZE = 100;
    private final int _blockSize;

    public ThreadedMultivariateFunction()
    {
        this(DEFAULT_BLOCK_SIZE);
    }

    public ThreadedMultivariateFunction(final int blockSize_)
    {
        _blockSize = blockSize_;
    }

    @Override
    public abstract int dimension();

    public abstract int resultSize(final int start_, final int end_);

    @Override
    public synchronized final void value(MultivariatePoint input_, int start_, int end_, EvaluationResult result_)
    {
        if (start_ == end_)
        {
            //do nothing.
            return;
        }
        if (start_ > end_)
        {
            throw new IllegalArgumentException("Start must be less than end.");
        }
        if (start_ < 0)
        {
            throw new IllegalArgumentException("Start must be nonnegative.");
        }

        prepare(input_);

        final int numRows = (end_ - start_);
        final int numTasks = 1 + (numRows / _blockSize);

        final List<FunctionTask> taskList = new ArrayList<>(numTasks);

        for (int i = 0; i < numTasks; i++)
        {
            final int thisStart = start_ + (i * _blockSize);

            if (thisStart > end_)
            {
                break;
            }

            final int blockEnd = thisStart + _blockSize;
            final int thisEnd = Math.min(end_, blockEnd);

            if (thisEnd == thisStart)
            {
                break;
            }

            final int taskSize = this.resultSize(thisStart, thisEnd);
            final FunctionTask task = new FunctionTask(thisStart, thisEnd, taskSize);
            taskList.add(task);

            if (USE_THREADS)
            {
                POOL.execute(task);
            }
            else
            {
                task.run();
                final EvaluationResult res = task.waitForCompletion();
                result_.add(res, result_.getHighWater(), res.getHighRow());
            }
        }

        if (USE_THREADS)
        {
            //Some synchronization to make sure we don't read old data.
            synchronized (result_)
            {
                for (final FunctionTask next : taskList)
                {
                    final EvaluationResult res = next.waitForCompletion();

                    synchronized (res)
                    {
                        result_.add(res, result_.getHighWater(), res.getHighRow());
                    }
                }
            }
        }
    }

    @Override
    public abstract int numRows();

    protected abstract void prepare(final MultivariatePoint input_);

    protected abstract void evaluate(final int start_, final int end_, EvaluationResult result_);

    @Override
    public EvaluationResult generateResult(int start_, int end_)
    {
        final int resultSize = this.resultSize(start_, end_);
        final EvaluationResult output = new EvaluationResult(resultSize);
        return output;
    }

    @Override
    public EvaluationResult generateResult()
    {
        return generateResult(0, this.numRows());
    }

    private final class FunctionTask extends GeneralTask<EvaluationResult>
    {
        private final int _start;
        private final int _end;
        private final int _evalCount;

        public FunctionTask(final int start_, final int end_, final int evalCount_)
        {
            if (end_ <= start_)
            {
                throw new IllegalArgumentException("Invalid.");
            }
            _start = start_;
            _end = end_;
            _evalCount = evalCount_;
        }

        @Override
        protected EvaluationResult subRun()
        {
            final EvaluationResult result = new EvaluationResult(_evalCount);

            synchronized (result)
            {
                ThreadedMultivariateFunction.this.evaluate(_start, _end, result);
            }

            return result;
        }

    }

}
