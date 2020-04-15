/*
 * Copyright 2014 Tyler Ward.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This code is part of the reference implementation of http://arxiv.org/abs/1409.6075
 *
 * This is provided as an example to help in the understanding of the ITEM model system.
 */
package edu.columbia.tjw.item.algo;

import edu.columbia.tjw.item.ItemRegressorReader;
import edu.columbia.tjw.item.data.InterpolatedCurve;

/**
 * @author tyler
 */
public final class QuantileStatistics
{
    private static final int BLOCK_SIZE = 10 * 1000;
    private static final double SIGMA_LIMIT = 3;
    private static final double RELATIVE_ERROR_THRESHOLD = 100.0;

    private final boolean _varTestPassed;
    private final QuantileBreakdown _approx;

    private final double[] _eY;
    private final double[] _devY;
    private final int[] _count;

    //Some stats on the global distribution (across all buckets).
    private final double _meanY;
    private final double _meanDevY;

    public QuantileStatistics(final QuantileStatistics base_, final double[] adjMean_, final double[] adjDev_)
    {
        final int size = base_._approx.getSize();

        if (size != adjMean_.length || size != adjDev_.length)
        {
            throw new IllegalArgumentException("Size mismatch.");
        }

        _varTestPassed = base_._varTestPassed;
        _approx = base_._approx;
        _count = base_._count;
        _meanY = base_._meanY;
        _meanDevY = base_._meanDevY;

        _eY = adjMean_.clone();
        _devY = adjDev_.clone();
    }


    private QuantileStatistics(final QuantileStatisticsBuilder builder_)
    {
        _approx = builder_._approx;
        _varTestPassed = builder_.checkConvergence();

        final int size = _approx.getSize();

        _eY = new double[size];
        _devY = new double[size];
        _count = new int[size];

        for (int i = 0; i < size; i++)
        {
            _eY[i] = builder_._calcs[i].getMean();
            _devY[i] = builder_._calcs[i].getDev();
            _count[i] = builder_._calcs[i].getCount();
        }

        _meanY = builder_._totalCalc.getMean();
        _meanDevY = Math.sqrt(builder_._totalCalc.getMeanVariance());
    }

    public static QuantileStatistics generate(final ItemRegressorReader xReader_, final ItemRegressorReader yReader_)
    {
        return generate(xReader_, yReader_, QuantileBreakdown.buildApproximation(xReader_));
    }

    public static QuantileStatistics generate(final ItemRegressorReader xReader_, final ItemRegressorReader yReader_,
                                              QuantileBreakdown breakdown_)
    {
        final int size = xReader_.size();

        if (yReader_.size() != size)
        {
            throw new IllegalArgumentException("Size mismatch: " + size + " != " + yReader_.size());
        }

        final QuantileStatisticsBuilder builder = builder(breakdown_);
        boolean passes = false;

        for (int i = 0; i < size; i++)
        {
            final double x = xReader_.asDouble(i);
            final double y = yReader_.asDouble(i);

            builder.append(x, y);

            //check to see if we're done here.
            if (0 == (i + 1) % BLOCK_SIZE)
            {
                passes = builder.checkConvergence();

                if (passes)
                {
                    break;
                }
            }
        }

        return builder.build();
    }

    public static QuantileStatisticsBuilder builder(final QuantileBreakdown approx_)
    {
        return new QuantileStatisticsBuilder(approx_);
    }

    public int getSize()
    {
        return _approx.getSize();
    }

    public QuantileBreakdown getQuantApprox()
    {
        return _approx;
    }

    public double getMeanY(final int index_)
    {
        return _eY[index_];
    }

    public double getMeanDevY(final int index_)
    {
        final double rawDev = getDevY(index_);
        final int count = getCount(index_);

        if (count > 1)
        {
            return rawDev / Math.sqrt(count);
        }

        return rawDev;
    }

    public double getDevY(final int index_)
    {
        return _devY[index_];
    }

    public int getCount(final int index_)
    {
        return _count[index_];
    }

    public double getMeanY()
    {
        return _meanY;
    }

    public double getMeanDevY()
    {
        return _meanDevY;
    }

    public boolean getVarTestPassed()
    {
        return _varTestPassed;
    }

    public InterpolatedCurve getValueCurve(final boolean linear_, final double alpha_)
    {
        final int firstIndex = _approx.firstStep(alpha_);
        final int lastIndex = _approx.lastStep(alpha_);
        return new InterpolatedCurve(this._approx.getXValues(), _eY, linear_, true, firstIndex, lastIndex);
    }


    public static final class QuantileStatisticsBuilder
    {
        private final QuantileBreakdown _approx;
        private final VarianceCalculator[] _calcs;
        private final VarianceCalculator _totalCalc;

        private QuantileStatisticsBuilder(final QuantileBreakdown approx_)
        {
            _approx = approx_;

            _calcs = new VarianceCalculator[_approx.getSize()];

            for (int i = 0; i < _calcs.length; i++)
            {
                _calcs[i] = new VarianceCalculator();
            }

            _totalCalc = new VarianceCalculator();
        }

        public boolean append(final double x_, final double y_)
        {
            if (Double.isNaN(x_) || Double.isInfinite(x_))
            {
                return false;
            }
            if (Double.isNaN(y_) || Double.isInfinite(y_))
            {
                return false;
            }

            final int index = _approx.findBucket(x_);
            _totalCalc.update(y_);
            _calcs[index].update(y_);
            return true;
        }

        public boolean checkConvergence()
        {
            boolean passes = true;
            final double limit2 = SIGMA_LIMIT * SIGMA_LIMIT;

            final double globalMeanY = _totalCalc.getMean();

            double prevMean = _calcs[0].getMean();
            double prevVar = _calcs[0].getMeanVariance();

            for (int w = 1; w < _approx.getSize(); w++)
            {
                final double ya = prevMean;
                final double yb = _calcs[w].getMean();
                final double va = prevVar;
                final double vb = _calcs[w].getMeanVariance();

                // Rotate these values.
                prevMean = yb;
                prevVar = vb;

                final double diff = (ya - yb);
                final double d2 = diff * diff;

                if (Math.abs(ya) > RELATIVE_ERROR_THRESHOLD * Math.sqrt(va))
                {
                    //If the bucket is very large compared to its relative error, then accept it as-is.
                    continue;
                }

                //Same as saying that the root squared diff is greater than several sigma.
                //i.e. we pulled in enough data that we can distinguish between these buckets.
                if (d2 < (limit2 * va) && d2 < (limit2 * vb))
                {
                    final double dMean = (ya - globalMeanY);
                    final double dm2 = dMean * dMean;

                    //They are allowed by either being different from neighboring buckets, or different
                    //from the mean. Essentially, we don't penalize buckets for being in a flat tail, provided
                    //that tail is not right at the mean (in which case, we really don't know much...).
                    if (dm2 < (limit2 * va))
                    {
                        passes = false;
                        break;
                    }
                }
            }

            return passes;
        }

        public QuantileStatistics build()
        {
            return new QuantileStatistics(this);
        }
    }


}
