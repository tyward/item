package edu.columbia.tjw.item.algo;

import org.apache.commons.math3.analysis.BivariateFunction;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.function.Add;

import java.io.Serializable;

public final class VectorTools
{
    private static final BivariateFunction ADD = new Add();

    private VectorTools()
    {
    }

    public static DoubleVector scalarMultiply(final DoubleVector input_, final double scalar_)
    {
        final SerializableUnivariateFunction multiplier = (double x) -> x * scalar_;
        return DoubleVector.apply(multiplier, input_);
    }

    public static DoubleVector add(final DoubleVector a_, final DoubleVector b_)
    {
        return DoubleVector.apply(ADD, a_, b_);
    }

    /**
     * Calculates a_ + scalar_ * b_
     *
     * @param a_
     * @param b_
     * @param scalar_
     * @return
     */
    public static DoubleVector multiplyAccumulate(final DoubleVector a_, final DoubleVector b_, final double scalar_)
    {
        return add(a_, scalarMultiply(b_, scalar_));
    }

    public static double maxAbsElement(final DoubleVector x_)
    {
        double maxAbs = 0.0;

        for (int i = 0; i < x_.getSize(); i++)
        {
            maxAbs = Math.max(maxAbs, Math.abs(x_.getEntry(i)));
        }

        return maxAbs;
    }

    public static double dot(final DoubleVector a_, final DoubleVector b_)
    {
        if (a_.getSize() != b_.getSize())
        {
            throw new IllegalArgumentException("Mismatched length: " + a_.getSize() + " != " + b_.getSize());
        }

        double dot = 0.0;

        for (int i = 0; i < a_.getSize(); i++)
        {
            dot += a_.getEntry(i) * b_.getEntry(i);
        }

        return dot;
    }

    public static double magnitude(final DoubleVector x_)
    {
        final double dot = dot(x_, x_);
        final double output = Math.sqrt(dot);
        return output;
    }

    public static double cos(final DoubleVector a_, final DoubleVector b_)
    {
        final double dot = dot(a_, b_);
        final double magA = magnitude(a_);
        final double magB = magnitude(b_);
        final double denom = magA * magB;

        if (0.0 == denom)
        {
            return 1.0;
        }

        final double cos = dot / (magA * magB);
        return cos;
    }

    private interface SerializableUnivariateFunction extends UnivariateFunction, Serializable
    {

    }
}
