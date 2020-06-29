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

import edu.columbia.tjw.item.ItemSettings;
import edu.columbia.tjw.item.fit.calculator.BlockCalculationType;
import edu.columbia.tjw.item.fit.calculator.FitPoint;
import edu.columbia.tjw.item.fit.calculator.FitPointAnalyzer;

/**
 * @param <F> The type of function this can optimize
 * @author tyler
 */
public abstract class Optimizer<F extends MultivariateOptimizationFunction>
{
    private static final double SQRT_EPSILON = Math.sqrt(Math.ulp(1.0));
    private static final double DEFAULT_XTOL = 0.0;
    private static final double DEFAULT_YTOL = 1.0e-6;

    private final double _stdDevThreshold;
    private final double _xTol;
    private final double _yTol;
    private final int _blockSize;
    private final int _maxEvalCount;

    private final FitPointAnalyzer _comparator;

    public Optimizer(final int blockSize_, final int maxEvalCount_, final OptimizationTarget target_,
                     ItemSettings settings_)
    {
        this(DEFAULT_XTOL, DEFAULT_YTOL, blockSize_, maxEvalCount_, target_, settings_);
    }

    public Optimizer(final double xTol_, final double yTol_, final int blockSize_, final int maxEvalCount_,
                     final OptimizationTarget target_, ItemSettings settings_)
    {
        _blockSize = blockSize_;
        _xTol = xTol_;
        _yTol = yTol_;
        _maxEvalCount = maxEvalCount_;
        _stdDevThreshold = settings_.getZScoreCutoff();

        _comparator = new FitPointAnalyzer(_blockSize, target_, settings_);
    }

    public abstract OptimizationResult optimize(final F f_, final MultivariatePoint startingPoint_,
                                                final MultivariatePoint direction_)
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

    /**
     * returns true if the relative error is too small to continue.
     *
     * @param aResult_
     * @param bResult_
     * @return
     */
    protected boolean checkYTolerance(final FitPoint aResult_, final FitPoint bResult_)
    {
        // Make sure everything has the same (approximate) level of computed results.
        final int highWater = Math.max(aResult_.getNextBlock(BlockCalculationType.VALUE),
                bResult_.getNextBlock(BlockCalculationType.VALUE));
        final double meanA = getComparator().computeObjective(aResult_, highWater);
        final double meanB = getComparator().computeObjective(bResult_, highWater);

        final double scaledDiff = Math.abs(meanA - meanB) / (meanA + meanB);
        return scaledDiff < this._yTol;
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

    protected boolean checkXTolerance(final MultivariatePoint a_, final MultivariatePoint b_)
    {
        final double scale = SQRT_EPSILON * 0.5 * (a_.getMagnitude() + b_.getMagnitude());
        return checkXTolerance(a_, b_, scale);

    }

    protected boolean checkXTolerance(final MultivariatePoint a_, final MultivariatePoint b_, final double target_)
    {
        final double distance = a_.distance(b_);
        return distance < target_;
    }

}
