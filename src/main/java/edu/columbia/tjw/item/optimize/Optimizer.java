/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.columbia.tjw.item.optimize;

import edu.columbia.tjw.item.optimize.OptimizationResult;
import edu.columbia.tjw.item.optimize.OptimizationFunction;
import edu.columbia.tjw.item.optimize.EvaluationResult;
import edu.columbia.item.rigel.optimize.EvaluationPoint;
import edu.columbia.tjw.item.optimize.ConvergenceException;
import edu.columbia.tjw.item.optimize.BasicAdaptiveComparator;
import edu.columbia.tjw.item.optimize.AdaptiveComparator;

/**
 *
 * @author tyler
 * @param <V>
 * @param <F>
 */
public abstract class Optimizer<V extends EvaluationPoint<V>, F extends OptimizationFunction<V>>
{
    private static final double DEFAULT_XTOL = 1.0e-6;
    private static final double DEFAULT_YTOL = 1.0e-6;
    private static final int DEFAULT_BLOCK_SIZE = 10000;

    private final double _stdDevThreshold = 3;
    private final double _xTol;
    private final double _yTol;
    private final int _blockSize;
    private final int _maxEvalCount;

    private final AdaptiveComparator<V, F> _comparator;

    public Optimizer(final int blockSize_, final int maxEvalCount_)
    {
        this(DEFAULT_XTOL, DEFAULT_YTOL, blockSize_, maxEvalCount_);
    }

    public Optimizer(final int maxEvalCount_)
    {
        this(DEFAULT_XTOL, DEFAULT_YTOL, DEFAULT_BLOCK_SIZE, maxEvalCount_);
    }

    public abstract OptimizationResult<V> optimize(final F f_, final V startingPoint_, final V direction_) throws ConvergenceException;

    public Optimizer(final double xTol_, final double yTol_, final int blockSize_, final int maxEvalCount_)
    {
        _blockSize = blockSize_;
        _xTol = xTol_;
        _yTol = yTol_;
        _maxEvalCount = maxEvalCount_;

        _comparator = new BasicAdaptiveComparator<V, F>(_blockSize, _stdDevThreshold);
    }

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

    public AdaptiveComparator<V, F> getComparator()
    {
        return _comparator;
    }

    /**
     * Figure out how far apart these two results could realistically be,
     * without attempting additional calculations.
     *
     * @param aResult_
     * @param bResult_
     * @return
     */
    protected boolean checkYTolerance(final EvaluationResult aResult_, final EvaluationResult bResult_)
    {
        final double meanA = aResult_.getMean();
        final double meanB = bResult_.getMean();

        final double stdDevA = aResult_.getStdDev();
        final double stdDevB = bResult_.getStdDev();

        final double raw = Math.abs(meanA - meanB);
        final double adjusted = raw + this._stdDevThreshold * (stdDevA + stdDevB);

        final double scale = Math.abs((meanA * meanA) + (meanB * meanB));

        final double scaled = adjusted / scale;

        final boolean output = scaled < this._yTol;
        return output;
    }

    protected boolean checkYTolerance(final EvaluationResult aResult_, final EvaluationResult bResult_, final EvaluationResult cResult_)
    {
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
