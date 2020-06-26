package edu.columbia.tjw.item.optimize;

import edu.columbia.tjw.item.algo.DoubleVector;
import edu.columbia.tjw.item.algo.VectorTools;
import edu.columbia.tjw.item.fit.calculator.FitPoint;

public final class UnivariateOptimizationFunction
{

    private final MultivariateOptimizationFunction _base;
    private final DoubleVector _a;
    private final DoubleVector _direction;
    private final double _scale;

    public UnivariateOptimizationFunction(final MultivariateOptimizationFunction base_, final DoubleVector a_,
                                          final DoubleVector direction_)
    {
        _base = base_;
        _a = a_;
        _direction = direction_;

        // Anything smaller than about sqrt(epsilon) times this value can't really be resolved.
        _scale = VectorTools.magnitude(a_) + VectorTools.magnitude(direction_);
    }


    public int numRows()
    {
        return _base.numRows();
    }

    public double scale()
    {
        return _scale;
    }

    public FitPoint evaluate(final double val_)
    {
        final DoubleVector next = VectorTools.multiplyAccumulate(_a, _direction, val_);
        return _base.evaluate(next);
    }

    public FitPoint evaluateGradient(final double val_)
    {
        final DoubleVector next = VectorTools.multiplyAccumulate(_a, _direction, val_);
        return _base.evaluateGradient(next);
    }

}
