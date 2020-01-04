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

import edu.columbia.tjw.item.fit.calculator.BlockCalculationType;
import edu.columbia.tjw.item.fit.calculator.FitPoint;
import edu.columbia.tjw.item.fit.calculator.FitPointAnalyzer;

/**
 * @param <V> The type of points over which this can optimize
 * @param <F> The type of function this can optimize
 * @author tyler
 */
public abstract class Optimizer<V extends EvaluationPoint<V>, F extends OptimizationFunction<V>>
{
    private static final double DEFAULT_XTOL = 1.0e-6;
    private static final double DEFAULT_YTOL = 1.0e-6;

    private final double _stdDevThreshold = 5.0;
    private final double _xTol;
    private final double _yTol;
    private final int _blockSize;
    private final int _maxEvalCount;

    private final FitPointAnalyzer _comparator;

    public Optimizer(final int blockSize_, final int maxEvalCount_)
    {
        this(DEFAULT_XTOL, DEFAULT_YTOL, blockSize_, maxEvalCount_);
    }

    public Optimizer(final double xTol_, final double yTol_, final int blockSize_, final int maxEvalCount_)
    {
        _blockSize = blockSize_;
        _xTol = xTol_;
        _yTol = yTol_;
        _maxEvalCount = maxEvalCount_;

        _comparator = new FitPointAnalyzer(_blockSize, _stdDevThreshold);
    }

    public abstract OptimizationResult<V> optimize(final F f_, final V startingPoint_, final V direction_)
            throws ConvergenceException;

    public double getXTolerance()
    {
        return _xTol;
    }

    public double getYTolerance()
    {
        return _yTol;
    }

    public int getBlockSize()
    {
        return _blockSize;
    }

    public int getMaxEvalCount()
    {
        return _maxEvalCount;
    }

    public FitPointAnalyzer getComparator()
    {
        return _comparator;
    }

    protected boolean checkYTolerance(final FitPoint aResult_, final FitPoint bResult_)
    {
        // Make sure everything has the same (approximate) level of computed results.
        final int highWater = Math.max(aResult_.getNextBlock(BlockCalculationType.VALUE),
                bResult_.getNextBlock(BlockCalculationType.VALUE));
        final double meanA = getComparator().computeObjective(aResult_, highWater);
        final double meanB = getComparator().computeObjective(bResult_, highWater);

        final double stdDevA = getComparator().computeObjectiveStdDev(aResult_, highWater);
        final double stdDevB = getComparator().computeObjectiveStdDev(bResult_, highWater);

        final double raw = Math.abs(meanA - meanB);
        final double adjusted = raw + this._stdDevThreshold * (stdDevA + stdDevB);

        final double scale = Math.abs((meanA * meanA) + (meanB * meanB));

        final double scaled = adjusted / scale;

        final boolean output = scaled < this._yTol;
        return output;
    }


    protected boolean checkYTolerance(final FitPoint aResult_, final FitPoint bResult_,
                                      final FitPoint cResult_)
    {
        // Make sure everything has the same (approximate) level of computed results.
        final BlockCalculationType valType = BlockCalculationType.VALUE;

        int highWater = Math.max(aResult_.getNextBlock(valType),
                bResult_.getNextBlock(valType));
        highWater = Math.max(cResult_.getNextBlock(valType), highWater);
        aResult_.computeUntil(highWater, valType);
        bResult_.computeUntil(highWater, valType);
        cResult_.computeUntil(highWater, valType);

        final boolean checkA = checkYTolerance(aResult_, bResult_);
        final boolean checkB = checkYTolerance(bResult_, cResult_);

        final boolean output = checkA && checkB;
        return output;
    }

    protected boolean checkXTolerance(final V a_, final V b_)
    {
        final double distance = a_.distance(b_);

        final double aMag = a_.getMagnitude();
        final double bMag = b_.getMagnitude();

        final double scale = Math.sqrt((aMag * aMag) + (bMag * bMag));

        final double result = distance / scale;

        final boolean output = result < this._xTol;
        return output;
    }

}
