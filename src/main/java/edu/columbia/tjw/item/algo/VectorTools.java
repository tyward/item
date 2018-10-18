package edu.columbia.tjw.item.algo;

public final class VectorTools
{
    private VectorTools()
    {
    }

    public static double dot(final double[] a_, final double[] b_)
    {
        double dot = 0.0;

        for (int i = 0; i < a_.length; i++)
        {
            dot += a_[i] * b_[i];
        }

        return dot;
    }

    public static double magnitude(final double[] x_)
    {
        final double dot = dot(x_, x_);
        final double output = Math.sqrt(dot);
        return output;
    }

    public static double cos(final double[] a_, final double[] b_)
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
}
