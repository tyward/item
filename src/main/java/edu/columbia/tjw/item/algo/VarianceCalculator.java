package edu.columbia.tjw.item.algo;

/**
 * Uses Welford's online algorithm.
 * https://en.wikipedia.org/wiki/Algorithms_for_calculating_variance
 */
public final class VarianceCalculator
{
    private int _count = 0;
    private double _mean = 0.0;
    private double _m2 = 0.0;

    public VarianceCalculator()
    {
    }

    public boolean update(final double input_)
    {
        if (Double.isNaN(input_) || Double.isInfinite(input_))
        {
            return false;
        }

        _count++;

        final double delta = input_ - _mean;
        _mean += delta / _count;
        final double delta2 = input_ - _mean;
        _m2 += delta * delta2;
        return true;
    }

    public int getCount()
    {
        return _count;
    }

    public double getMean()
    {
        return _mean;
    }

    public double getDev()
    {
        return Math.sqrt(getVariance());
    }

    public double getMeanVariance()
    {
        if (_count <= 1)
        {
            return 0.0;
        }

        return getVariance() / _count;
    }

    public double getMeanDev()
    {
        return Math.sqrt(getMeanVariance());
    }

    public double getVariance()
    {
        if (_count <= 1)
        {
            return 0;
        }

        final double var2 = _m2 / (_count - 1);
        return var2;
    }


}
