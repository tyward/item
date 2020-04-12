package edu.columbia.tjw.item.algo;

import edu.columbia.tjw.item.ItemRegressorReader;

import java.util.Arrays;

public final class GKQuantileBreakdown implements QuantileBreakdown
{
    public static final int DEFAULT_BUCKETS = 100;

    private final int _bucketCount;
    private final double _epsilon;
    private final GKQuantiles _quantiles;
    private final VarianceCalculator _varCalc;
    private final double[] _xVals;


    private GKQuantileBreakdown(final GKQuantileBreakdown prev_, final int bucketCount_)
    {
        if (bucketCount_ > prev_._bucketCount)
        {
            throw new IllegalArgumentException("Cannot re-bucket to a larger bucket count");
        }
        if (bucketCount_ <= 0)
        {
            throw new IllegalArgumentException("Bucket count must be positive.");
        }

        _bucketCount = bucketCount_;
        _epsilon = 1.0 / _bucketCount;
        _quantiles = prev_._quantiles;
        _varCalc = prev_._varCalc;
        _xVals = new double[_bucketCount];

        for (int i = 0; i < _xVals.length; i++)
        {
            final double next = _epsilon * i;
            _xVals[i] = _quantiles.getQuantile(next);
        }
    }

    public GKQuantileBreakdown(final ItemRegressorReader regressor_)
    {
        _bucketCount = DEFAULT_BUCKETS;
        _epsilon = 1.0 / _bucketCount;
        _quantiles = new GKQuantiles(0.5 * _epsilon);
        _varCalc = new VarianceCalculator();

        for (int i = 0; i < regressor_.size(); i++)
        {
            final double x = regressor_.asDouble(i);
            _quantiles.offer(x);
            _varCalc.update(x);
        }

        _xVals = new double[_bucketCount];

        for (int i = 0; i < _xVals.length; i++)
        {
            final double next = _epsilon * i;
            _xVals[i] = _quantiles.getQuantile(next);
        }
    }


    @Override
    public int getSize()
    {
        return _bucketCount;
    }

    @Override
    public int findBucket(double x_)
    {
        final int findIndex = Arrays.binarySearch(_xVals, x_);

        if (findIndex >= 0)
        {
            return findIndex;
        }

        // This is the first element greater than x_, we want previous one.
        final int insertionPoint = (-findIndex) - 1;
        final int actIndex = insertionPoint - 1;

        return Math.max(0, actIndex);
    }

    @Override
    public int firstStep(double alpha_)
    {
        if (alpha_ == 0)
        {
            return 0;
        }
        if (Double.isNaN(alpha_))
        {
            throw new IllegalArgumentException("NaN alpha.");
        }
        if (alpha_ < 0 || alpha_ >= 0.5)
        {
            throw new IllegalArgumentException("Alpha (for trimming) must be in [0, 0.5): " + alpha_);
        }

        final int firstStep = (int) Math.round(alpha_ * getSize());
        return firstStep;
    }

    @Override
    public int lastStep(double alpha_)
    {
        final int firstStep = firstStep(alpha_);
        final int steps = this.getSize();
        final int lastStep = steps - firstStep;
        return lastStep;
    }

    @Override
    public double[] getXValues()
    {
        return _xVals.clone();
    }

    @Override
    public double getMean()
    {
        return _varCalc.getMean();
    }

    @Override
    public double getBucketMean(int index_)
    {
        return _xVals[index_];
    }

    @Override
    public int getTotalCount()
    {
        return _quantiles.getCount();
    }

    @Override
    public double getMeanStdDev()
    {
        return _varCalc.getMeanDev();
    }

    @Override
    public QuantileBreakdown rebucket(int bucketCount_)
    {
        return new GKQuantileBreakdown(this, bucketCount_);
    }
}
