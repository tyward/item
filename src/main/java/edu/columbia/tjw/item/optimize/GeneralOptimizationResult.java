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
import edu.columbia.tjw.item.fit.calculator.BlockCalculationType;
import edu.columbia.tjw.item.fit.calculator.FitPoint;

/**
 * @author tyler
 */
public class GeneralOptimizationResult implements OptimizationResult
{
    private final FitPoint _minResult;
    private final boolean _converged;
    private final int _evalCount;

    public GeneralOptimizationResult(final FitPoint minResult_, final boolean converged_,
                                     final int evalCount_)
    {
        if (null == minResult_)
        {
            throw new NullPointerException("Evaluation result cannot be null.");
        }
        if (evalCount_ < 0)
        {
            throw new IllegalArgumentException("Eval count must be nonnegative: " + evalCount_);
        }

        _minResult = minResult_;
        _converged = converged_;
        _evalCount = evalCount_;
    }

    @Override
    public final DoubleVector getOptimum()
    {
        return _minResult.getParameters();
    }

    @Override
    public final boolean converged()
    {
        return _converged;
    }

    @Override
    public final int evaluationCount()
    {
        return _evalCount;
    }

    @Override
    public final double minEntropy()
    {
        if (_minResult.getNextBlock(BlockCalculationType.VALUE) < 1)
        {
            // If we really don't know the answer, just say so.
            return Double.NaN;
        }

        return _minResult.getAggregated(BlockCalculationType.VALUE).getEntropyMean();
    }

    @Override
    public final FitPoint minResult()
    {
        return _minResult;
    }

    @Override
    public int dataElementCount()
    {
        return _minResult.getSize();
    }
}
