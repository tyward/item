package edu.columbia.tjw.item.algo;

import edu.columbia.tjw.item.util.HashUtil;
import org.apache.commons.math3.analysis.BivariateFunction;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.function.Add;
import org.apache.commons.math3.analysis.function.Subtract;

import java.io.Serializable;

public final class VectorTools
{
    private static final BivariateFunction ADD = new Add();
    private static final BivariateFunction SUBTRACT = new Subtract();

    private VectorTools()
    {
    }

    public static boolean isWellDefined(final DoubleVector input_)
    {
        for (int i = 0; i < input_.getSize(); i++)
        {
            final double val = input_.getEntry(i);

            if (Double.isNaN(val) || Double.isInfinite(val))
            {
                return false;
            }
        }

        return true;
    }

    public static DoubleVector scalarMultiply(final DoubleVector input_, final double scalar_)
    {
        final SerializableUnivariateFunction multiplier = (double x) -> x * scalar_;
        return DoubleVector.apply(multiplier, input_);
    }

    public static DoubleVector subtract(final DoubleVector a_, final DoubleVector b_)
    {
        return DoubleVector.apply(SUBTRACT, a_, b_);
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
        // This is more efficient, as it avoids one more object creation and indirection.
        final SerializableBivariateFunction mac = (double x, double y) ->
        {
            return x + (y * scalar_);
        };
        return DoubleVector.apply(mac, a_, b_);
        //return add(a_, scalarMultiply(b_, scalar_));
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

    public static double distance(final DoubleVector x_, final DoubleVector y_)
    {
        if (x_.getSize() != y_.getSize())
        {
            throw new IllegalArgumentException("Mismatched length: " + x_.getSize() + " != " + y_.getSize());
        }

        double sum = 0.0;

        for (int i = 0; i < x_.getSize(); i++)
        {
            final double term = (x_.getEntry(i) - y_.getEntry(i));

            sum += term * term;
        }

        return Math.sqrt(sum);
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

    public static int hashCode(final int startHash_, final DoubleVector vector_)
    {
        int hash = HashUtil.mix(startHash_, vector_.getSize());

        for (int i = 0; i < vector_.getSize(); i++)
        {
            hash = HashUtil.mix(startHash_, Double.doubleToLongBits(vector_.getSize()));
        }

        return hash;
    }

    public static boolean equals(final DoubleVector a_, final DoubleVector b_)
    {
        if (a_ == b_)
        {
            return true;
        }
        if (null == a_ || null == b_)
        {
            return false;
        }

        final int size = a_.getSize();

        if (size != b_.getSize())
        {
            return false;
        }

        for (int i = 0; i < size; i++)
        {
            if (a_.getEntry(i) != b_.getEntry(i))
            {
                return false;
            }
        }

        return true;
    }

    public static String toString(final DoubleVector vector_)
    {
        final StringBuilder builder = new StringBuilder();

        if (null == vector_)
        {
            builder.append("<null>");
            return builder.toString();
        }

        builder.append(vector_.getClass().getName() + ": [");

        for (int i = 0; i < vector_.getSize(); i++)
        {
            if (i != 0)
            {
                builder.append(", ");
            }

            builder.append(vector_.getEntry(i));
        }

        builder.append("]");
        return builder.toString();
    }

    private interface SerializableUnivariateFunction extends UnivariateFunction, Serializable
    {

    }

    private interface SerializableBivariateFunction extends BivariateFunction, Serializable
    {

    }
}

