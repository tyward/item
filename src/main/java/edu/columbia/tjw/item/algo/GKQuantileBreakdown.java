package edu.columbia.tjw.item.algo;

import edu.columbia.tjw.item.ItemRegressorReader;

import java.util.Arrays;

public final class GKQuantileBreakdown implements QuantileBreakdown
{
    // There is some evidence that the non-uniform buckets produced without simple bucketing perform a bit better.
    public static final boolean USE_SIMPLE_BUCKETS = true;
    public static final boolean USE_APPROX_BUCKETS = false;

    public static final int DEFAULT_BUCKETS = 100;

    public static final int MIN_SIZE = 10 * 1000;
    public static final int MIN_FILL = 100;

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
        _xVals = generateBuckets(_quantiles, _bucketCount, _epsilon);
    }

    public GKQuantileBreakdown(final ItemRegressorReader regressor_)
    {
        _bucketCount = DEFAULT_BUCKETS;
        _epsilon = 1.0 / _bucketCount;
        _quantiles = new GKQuantiles(0.5 * _epsilon);
        _varCalc = new VarianceCalculator();

        final int blockSize = MIN_FILL * _bucketCount;
        double[] buckets = null;

        outer:
        for (int i = 0; i < regressor_.size(); i++)
        {
            final double x = regressor_.asDouble(i);

            if (Double.isNaN(x) || Double.isInfinite(x))
            {
                continue;
            }

            _quantiles.offer(x);
            _varCalc.update(x);

            if (USE_APPROX_BUCKETS && _varCalc.getCount() % blockSize == 0)
            {
                // Let's see if this has stablized, so we don't need to get any more data.
                final double[] nextBuckets = generateBuckets(_quantiles, _bucketCount, _epsilon);

                if (buckets == null)
                {
                    buckets = nextBuckets;
                    continue;
                }

                if (nextBuckets.length != buckets.length)
                {
                    continue;
                }
                if (nextBuckets[0] != buckets[0]
                        || nextBuckets[buckets.length - 1] != buckets[buckets.length - 1])
                {
                    // First and last elements must match.
                    continue;
                }

                for (int w = 1; w < nextBuckets.length - 1; w++)
                {
                    final double nextW = nextBuckets[w];
                    final double prevWm = buckets[w - 1];
                    final double prevWp = buckets[w + 1];

                    // We require that the elements are in the same order, but not necessarily equal.
                    if (nextW < prevWm || nextW > prevWp)
                    {
                        continue outer;
                    }
                }

                // These quantiles are essentially equivalent, no need to compute further.
                _xVals = nextBuckets;
                return;
            }
        }

        _xVals = generateBuckets(_quantiles, _bucketCount, _epsilon);
    }

    private static double[] generateBuckets(final GKQuantiles quantiles_,
                                            final int bucketCount_, final double epsilon_)
    {
        if (USE_SIMPLE_BUCKETS)
        {
            final double[] xVals = new double[bucketCount_];

            for (int i = 0; i < xVals.length; i++)
            {
                final double next = epsilon_ * i;
                xVals[i] = quantiles_.getQuantile(next);
            }

            return xVals;
        }
        else
        {
            final double[] xVals = new double[bucketCount_];
            xVals[0] = quantiles_.getQuantile(0.0);
            int pointer = 1;

            for (int i = 1; i < xVals.length; i++)
            {
                final double next = epsilon_ * i;
                final double nextVal = quantiles_.getQuantile(next);

                if (nextVal == xVals[pointer - 1])
                {
                    // This is the same as the previous bucket, replicate old behavior.
                    continue;
                }

                xVals[pointer++] = nextVal;
            }

            if (pointer < xVals.length)
            {
                return Arrays.copyOf(xVals, pointer);
            }
            else
            {
                return xVals;
            }
        }
    }

    @Override
    public int getSize()
    {
        return _xVals.length;
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
