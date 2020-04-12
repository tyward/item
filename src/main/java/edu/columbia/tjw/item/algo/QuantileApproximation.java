package edu.columbia.tjw.item.algo;

import edu.columbia.tjw.item.ItemRegressorReader;

import java.util.Arrays;

public final class QuantileApproximation implements QuantileBreakdown
{
    public static final int DEFAULT_LOAD = 10;
    public static final int DEFAULT_BUCKETS = 100;

    private final double[] _cutoffValues;
    private final double[] _meanValues;
    private final int[] _counts;
    private final boolean[] _identicalValues;
    private final int _totalCount;

    private final double _mean;
    private final double _variance;


    private QuantileApproximation(final QuantileApproximationBuilder builder_)
    {
        final int size = builder_._bucketCount;

        _cutoffValues = new double[size];
        _meanValues = new double[size];
        _counts = new int[size];
        _identicalValues = new boolean[size];

        int totalCount = 0;

        for (int i = 0; i < size; i++)
        {
            final QuantileApproximationBuilder.Bucket nextBucket = builder_._buckets[i];
            _cutoffValues[i] = builder_._minValues[i];
            _meanValues[i] = nextBucket.getMean();
            _counts[i] = nextBucket.getCount();
            _identicalValues[i] = !nextBucket._canSplit;
            totalCount += _counts[i];


        }

        if (totalCount <= 0)
        {
            throw new IllegalArgumentException("Total count cannot be zero.");
        }

        for (int i = 0; i < size; i++)
        {
            // Let's make sure this is sane where we may have zero count.
            if (_counts[i] != 0)
            {
                continue;
            }

            // We know for sure that size is at least 2.
            if (i == 0)
            {
                // The zeroth bucket would have a -infty cutoff value, let's not go crazy here.
                _meanValues[i] = _cutoffValues[i + 1] - 1.0;
            }
            else if (i == size - 1)
            {
                // This is the last bucket, do  something similar.
                _meanValues[i] = _cutoffValues[i] + 1.0;
            }
            else
            {
                // Let's assume the mean would have been halfway through this bucket.
                final double floor = _cutoffValues[i];
                final double ceiling = _cutoffValues[i + 1];
                final double inferred = (ceiling + floor) * 0.5;
                _meanValues[i] = inferred;
            }
        }


        _totalCount = totalCount;
        _mean = builder_._calc.getMean();
        _variance = builder_._calc.getVariance();
    }

    private QuantileApproximation(QuantileApproximation base_, int bucketCount_)
    {
        final int size = bucketCount_;

        _totalCount = base_._totalCount;
        _mean = base_._mean;
        _variance = base_._variance;

        _cutoffValues = new double[size];
        _meanValues = new double[size];
        _counts = new int[size];
        _identicalValues = new boolean[size];

        Arrays.fill(_identicalValues, true);

        final int targetSize = _totalCount / size;
        int scanPointer = 0;

        for (int i = 0; i < size; i++)
        {
            while (scanPointer < base_._counts.length && _counts[i] < targetSize)
            {
                if (_counts[i] > 0)
                {
                    // this includes at least two buckets, values can't be identical.
                    _identicalValues[i] = false;
                }
                else
                {
                    _cutoffValues[i] = base_._cutoffValues[scanPointer];
                }

                _counts[i] += base_._counts[scanPointer];

                _identicalValues[i] &= base_._identicalValues[scanPointer];
                _meanValues[i] += base_._meanValues[scanPointer] * base_._counts[scanPointer];
                scanPointer++;
            }

            _meanValues[i] /= _counts[i];
        }
    }


    public static QuantileBreakdown buildApproximation(final ItemRegressorReader xReader_)
    {
        final QuantileApproximation.QuantileApproximationBuilder qab = QuantileApproximation.builder();

        for (int i = 0; i < xReader_.size(); i++)
        {
            final double x = xReader_.asDouble(i);
            qab.addObservation(x);
        }

        return qab.build();
    }

    public QuantileApproximation rebucket(final int bucketCount_)
    {
        if (bucketCount_ > _meanValues.length)
        {
            throw new IllegalArgumentException("Cannot re-bucket to a larger bucket count");
        }
        if (bucketCount_ <= 0)
        {
            throw new IllegalArgumentException("Bucket count must be positive.");
        }

        return new QuantileApproximation(this, bucketCount_);
    }

    private static QuantileApproximationBuilder builder()
    {
        return new QuantileApproximationBuilder(DEFAULT_LOAD, DEFAULT_BUCKETS);
    }

    private static int calculateBucket(final double[] vals, final double x_, final int count_)
    {
        final int findIndex = Arrays.binarySearch(vals, 0, count_, x_);

        if (findIndex >= 0)
        {
            return findIndex;
        }


        // This is the first element greater than x_, we want previous one.
        final int insertionPoint = (-findIndex) - 1;
        final int actIndex = insertionPoint - 1;

        return actIndex;
    }

    public int getTotalCount()
    {
        return _totalCount;
    }

    public int getSize()
    {
        return _cutoffValues.length;
    }

    private int getBucketCount(final int index_)
    {
        return _counts[index_];
    }

    public double getBucketMean(final int index_)
    {
        return _meanValues[index_];
    }

    private boolean isBucketUniform(final int index_)
    {
        return _identicalValues[index_];
    }

    public double getMean()
    {
        return _mean;
    }

    public double getStdDev()
    {
        return Math.sqrt(_variance);
    }

    public double getMeanStdDev()
    {
        return Math.sqrt(_variance / _totalCount);
    }

    public int findBucket(final double x_)
    {
        return calculateBucket(_cutoffValues, x_, _cutoffValues.length);
    }

    /**
     * Returns the first bucket that would be valid for the given alpha trim.
     *
     * @param alpha_
     * @return
     */
    public final int firstStep(final double alpha_)
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

        final int steps = this.getSize();
        final int firstStep = (int) Math.round(alpha_ * steps);

        if (firstStep > (getSize() / 2))
        {
            throw new IllegalArgumentException("Alpha trim is too high, would discard all buckets.");
        }

        return firstStep;
    }

    /**
     * Returns the bucket after the last bucket that would be valid for the given alpha trim.
     * <p>
     * This would be getSize() for an alpha trim of 0.0
     *
     * @param alpha_
     * @return
     */
    public final int lastStep(final double alpha_)
    {
        final int firstStep = firstStep(alpha_);
        final int steps = this.getSize();
        final int lastStep = steps - firstStep;
        return lastStep;
    }

    public final double[] getXValues()
    {
        return _meanValues.clone();
    }

    private static final class QuantileApproximationBuilder
    {
        private final int _loadFactor;
        private final int _maxBuckets;

        private final double[] _minValues;
        private final Bucket[] _buckets;
        private final VarianceCalculator _calc;
        private int _bucketCount;

        private QuantileApproximationBuilder(final QuantileApproximationBuilder base_)
        {
            _loadFactor = base_._loadFactor;
            _maxBuckets = base_._maxBuckets;
            _bucketCount = base_._bucketCount;
            _minValues = base_._minValues.clone();
            _buckets = new Bucket[_maxBuckets];
            _calc = new VarianceCalculator();

            for (int i = 0; i < _bucketCount; i++)
            {
                _buckets[i] = new Bucket();
            }
        }

        private QuantileApproximationBuilder(final int loadFactor_, final int maxBuckets_)
        {
            if (loadFactor_ < 10)
            {
                throw new IllegalArgumentException("Invalid load factor.");
            }
            if (maxBuckets_ < 2)
            {
                throw new IllegalArgumentException("Invalid bucket count.");
            }

            _loadFactor = loadFactor_;
            _maxBuckets = maxBuckets_;
            _minValues = new double[_maxBuckets];
            _buckets = new Bucket[_maxBuckets];
            _bucketCount = 0;
            _calc = new VarianceCalculator();
            Arrays.fill(_minValues, Double.NaN);
        }

        public QuantileApproximation build()
        {
            return new QuantileApproximation(this);
        }

        public QuantileApproximationBuilder refresh()
        {
            return new QuantileApproximationBuilder(this);
        }

        public int getLightestMass()
        {
            int lightestMass = Integer.MAX_VALUE;

            for (int i = 0; i < _bucketCount; i++)
            {
                lightestMass = Math.min(lightestMass, _buckets[i].getMass());
            }

            return lightestMass;
        }

        public boolean addObservation(final double x_)
        {
            if (Double.isNaN(x_) || Double.isInfinite(x_))
            {
                return false;
            }

            _calc.update(x_);

            if (_bucketCount < 1)
            {
                final Bucket first = new Bucket();
                first.addObservation(x_);
                _buckets[0] = first;
                _minValues[0] = Double.NEGATIVE_INFINITY;
                _bucketCount++;
                return true;
            }

            final int actIndex = calculateBucket(_minValues, x_, _bucketCount);
            _buckets[actIndex].addObservation(x_);
            maybeSplit(actIndex);
            return true;
        }


        private void maybeSplit(final int index_)
        {
            final Bucket preSplit = _buckets[index_];
            final int mass = preSplit.getMass();

            if (mass < 2 * _loadFactor)
            {
                // Not enough elements to split.
                return;
            }
            if (!preSplit.getCanSplit())
            {
                // Uniform values, can't split.
                return;
            }
            if (_bucketCount == _maxBuckets)
            {
                // No more room.
                return;
            }

            final double mean = preSplit.getMean();
            final Bucket low = new Bucket();
            final Bucket high = new Bucket();

            low._minValue = preSplit._minValue;
            high._maxValue = preSplit._maxValue;

            // Stats are computed afresh each time, but keep in mind that
            // these buckets probably already have a fair bit of mass to them,
            // so we need to keep track of that.
            low._splitMass = (mass / 2);
            high._splitMass = (mass / 2);

            for (int i = _bucketCount; i > (index_ + 1); i--)
            {
                _minValues[i] = _minValues[i - 1];
                _buckets[i] = _buckets[i - 1];
            }

            //_minValues[index_] = preSplit._minValue;
            _buckets[index_] = low;
            _minValues[index_ + 1] = mean;
            _buckets[index_ + 1] = high;
            _bucketCount++;

            for (int i = 0; i < _bucketCount; i++)
            {
                if (_buckets[i] == null)
                {
                    System.out.println("Boing.");
                }
            }

        }


        private static final class Bucket
        {
            private double _minValue = Double.MAX_VALUE;
            private double _maxValue = Double.MIN_VALUE;
            private double _valSum = Double.NaN;
            private int _count = 0;
            private int _splitMass = 0;

            private double _exemplarValue = Double.NaN;
            private boolean _canSplit = false;

            public Bucket()
            {
            }

            public void addObservation(final double x_)
            {
                if (Double.isNaN(_exemplarValue))
                {
                    _exemplarValue = x_;
                    _valSum = x_;
                }
                else
                {
                    _valSum += x_;
                }

                _minValue = Math.min(_minValue, x_);
                _maxValue = Math.max(_maxValue, x_);

                _count++;

                _canSplit = _canSplit || (x_ != _exemplarValue);
            }

            public double getMean()
            {
                if (_count < 1)
                {
                    return 0.0;
                }

                return _valSum / _count;
            }

            public double getMin()
            {
                return _minValue;
            }

            public double getMax()
            {
                return _maxValue;
            }

            public int getMass()
            {
                return _splitMass + _count;
            }

            public int getCount()
            {
                return _count;
            }

            public boolean getCanSplit()
            {
                return _canSplit;
            }
        }
    }

}
