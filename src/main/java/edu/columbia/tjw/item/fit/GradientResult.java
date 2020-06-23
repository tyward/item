package edu.columbia.tjw.item.fit;

import edu.columbia.tjw.item.algo.DoubleVector;
import edu.columbia.tjw.item.algo.VectorTools;
import edu.columbia.tjw.item.optimize.OptimizationTarget;

import java.io.Serializable;
import java.util.Arrays;

public final class GradientResult implements Serializable
{
    private static final long serialVersionUID = 0x606e4b6c2343db26L;

    private final OptimizationTarget _target;
    private final DoubleVector _gradient;
    private final DoubleVector _fdGradient;
    private final DoubleVector _gradientAdjustment;
    private final double _objective;
    private final double _cos;
    private final double _fdMag;
    private final double _gradMag;

    public GradientResult(final OptimizationTarget target_, final double objective_, final DoubleVector gradient_,
                          final DoubleVector fdGradient_, final DoubleVector gradAdj_)
    {
        _target = target_;
        _gradient = gradient_;
        _fdGradient = fdGradient_;
        _gradientAdjustment = gradAdj_;

        _objective = objective_;
        _cos = VectorTools.cos(_gradient, _fdGradient);
        _gradMag = VectorTools.magnitude(_gradient);
        _fdMag = VectorTools.magnitude(_fdGradient);
    }

    public double getObjective()
    {
        return _objective;
    }

    public double getCos()
    {
        return _cos;
    }

    public DoubleVector getGradient()
    {
        return _gradient;
    }

    public DoubleVector getFdGradient()
    {
        return _fdGradient;
    }

    public DoubleVector getGradientAdjustment()
    {
        return _gradientAdjustment;
    }

    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("GradientResult[" + Integer.toHexString(System.identityHashCode(this)) + "][\n");
        builder.append("\nTarget: " + _target);
        builder.append("\nTarget: " + _objective);
        builder.append("\nCos: " + _cos);
        builder.append("\nGrad Mag: " + _gradMag);
        builder.append("\nFD Mag: " + _fdMag);
        builder.append("\nGradient: " + Arrays.toString(_gradient.copyOfUnderlying()));
        builder.append("\nFdGradient: " + Arrays.toString(_fdGradient.copyOfUnderlying()));
        builder.append("\n]");
        return builder.toString();
    }
}
