package edu.columbia.tjw.item.algo;

import java.util.Arrays;

public final class QuantileApproximation
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

        _totalCount = totalCount;
        _mean = builder_._calc.getMean();
        _variance = builder_._calc.getVariance();
    }

    public int getTotalCount()
    {
        return _totalCount;
    }

    public int getSize()
    {
        return _cutoffValues.length;
    }

    public int getBucketCount(final int index_)
    {
        return _counts[index_];
    }

    public double getBucketMean(final int index_)
    {
        return _meanValues[index_];
    }

    public boolean isBucketUniform(final int index_)
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

    public int findBucket(final double x_)
    {
        return calculateBucket(_cutoffValues, x_, _cutoffValues.length);
    }

    public static QuantileApproximationBuilder builder()
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


    public static final class QuantileApproximationBuilder
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
