package edu.columbia.tjw.item.fit;

import edu.columbia.tjw.item.optimize.OptimizationTarget;
import edu.columbia.tjw.item.util.MathTools;

import java.io.Serializable;
import java.util.Arrays;

public final class GradientResult implements Serializable
{
    private static final long serialVersionUID = 0x606e4b6c2343db26L;

    private final OptimizationTarget _target;
    private final double[] _gradient;
    private final double[] _fdGradient;
    private final double[] _gradientAdjustment;
    private final double _objective;
    private final double _cos;
    private final double _fdMag;
    private final double _gradMag;

    public GradientResult(final OptimizationTarget target_, final double objective_, final double[] gradient_,
                          final double[] fdGradient_, final double[] gradAdj_)
    {
        _target = target_;
        _gradient = gradient_;
        _fdGradient = fdGradient_;
        _gradientAdjustment = gradAdj_;

        _objective = objective_;
        _cos = MathTools.cos(_gradient, _fdGradient);
        _gradMag = MathTools.magnitude(_gradient);
        _fdMag = MathTools.magnitude(_fdGradient);
    }

    public double getObjective()
    {
        return _objective;
    }

    public double getCos()
    {
        return _cos;
    }

    public double[] getGradient()
    {
        return _gradient.clone();
    }

    public double[] getFdGradient()
    {
        return _fdGradient.clone();
    }

    public double[] getGradientAdjustment()
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
        builder.append("\nGradient: " + Arrays.toString(_gradient));
        builder.append("\nFdGradient: " + Arrays.toString(_fdGradient));
        builder.append("\n]");
        return builder.toString();
    }
}
