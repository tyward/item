/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.columbia.tjw.item.optimize;

/**
 *
 * @author tyler
 */
public class GeneralOptimizationResult<V extends EvaluationPoint<V>> implements OptimizationResult<V>
{
    private final V _optimum;
    private final EvaluationResult _minResult;
    private final boolean _converged;
    private final int _evalCount;

    public GeneralOptimizationResult(final V optimum_, final EvaluationResult minResult_, final boolean converged_, final int evalCount_)
    {
        if (null == optimum_)
        {
            throw new NullPointerException("Optimum cannot be null.");
        }
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
        _optimum = optimum_;
    }

    @Override
    public final V getOptimum()
    {
        return _optimum;
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
    public final double minValue()
    {
        return _minResult.getMean();
    }

    @Override
    public final EvaluationResult minResult()
    {
        return _minResult;
    }

    @Override
    public int dataElementCount()
    {
        return _minResult.getHighWater();
    }
}
