package edu.columbia.tjw.item.algo;

/**
 * TODO: Use Welford's online algorithm.
 * https://en.wikipedia.org/wiki/Algorithms_for_calculating_variance
 */
public final class VarianceCalculator
{
    private double _sum = 0.0;
    private double _sum2 = 0.0;
    private int _count = 0;

    public VarianceCalculator()
    {
    }

    public boolean update(final double input_)
    {
        if (Double.isNaN(input_) || Double.isInfinite(input_))
        {
            return false;
        }

        _sum += input_;
        _sum2 += (input_ * input_);
        _count++;
        return true;
    }

    public int getCount()
    {
        return _count;
    }

    public double getMean()
    {
        if (_count < 1)
        {
            return 0.0;
        }

        return _sum / _count;
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

    public double getVariance()
    {
        if (_count <= 1)
        {
            return 0;
        }

        final double eX = _sum / _count;
        final double eX2 = _sum2 / _count;
        final double var = eX2 - (eX * eX);
        return Math.max(var, 0.0);
    }


}
