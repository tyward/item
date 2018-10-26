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

import edu.columbia.tjw.item.algo.QuantApprox.QuantileNode;
import edu.columbia.tjw.item.data.InterpolatedCurve;
import edu.columbia.tjw.item.util.MathFunctions;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author tyler
 */
public final class QuantileDistribution implements Serializable
{
    private static final long serialVersionUID = 1116222802982789849L;

    private final RegressorQuantile _rQuantile;
    private final double[] _eY;
    private final double[] _devY;

    //Some stats on the global distribution (across all buckets).
    private final double _meanY;
    private final double _meanDevY;

    public QuantileDistribution(final double[] eX_, final double[] eY_, final double[] devX_, final double[] devY_, final long[] count_, final boolean doCopy_)
    {
        _rQuantile = new RegressorQuantile(eX_, devX_, count_, doCopy_);
        final int size = eX_.length;

        if (size != eY_.length || size != devX_.length || size != devY_.length || size != count_.length)
        {
            throw new IllegalArgumentException("Length mismatch.");
        }

        double sumX = 0.0;
        double sumY = 0.0;
        double sumX2 = 0.0;
        double sumY2 = 0.0;

        long count = 0;

        if (doCopy_)
        {
            _eY = eY_.clone();
            _devY = devY_.clone();
        }
        else
        {
            _eY = eY_;
            _devY = devY_;
        }

        for (int i = 0; i < size; i++)
        {
            final long bucketCount = _rQuantile.getCount(i);
            final double eYTerm = _eY[i];

            final double termY = eYTerm * bucketCount;
            final double termY2 = bucketCount * eYTerm * eYTerm;

            sumY += termY;
            sumY2 += termY2;

            count += bucketCount;
        }

        _meanY = sumY / count;
        _meanDevY = Math.sqrt(DistMath.computeMeanVariance(sumY, sumY2, count));
    }

    public QuantileDistribution(final QuantApprox approx_)
    {
        _rQuantile = new RegressorQuantile(approx_);
        final List<QuantileNode> nodes = new ArrayList<>(approx_.size());

        for (final QuantileNode next : approx_)
        {
            if (next.getCount() > 0)
            {
                nodes.add(next);
            }
        }

        final int approxSize = nodes.size();

        _eY = new double[approxSize];
        _devY = new double[approxSize];

        long totalCount = 0;

        int pointer = 0;

        for (final QuantileNode next : nodes)
        {
            _eY[pointer] = next.getMeanY();
            _devY[pointer] = next.getStdDevY();

            final long lc = next.getCount();
            totalCount += lc;
            pointer++;
        }

        _meanY = approx_.getMeanY();
        _meanDevY = approx_.getStdDevY();
    }

    public QuantileDistribution alphaTrim(final double alpha_)
    {
        if (alpha_ == 0)
        {
            return this;
        }

        if (alpha_ < 0 || alpha_ >= 0.5)
        {
            throw new IllegalArgumentException("Alpha (for trimming) must be in [0, 0.5]: " + alpha_);
        }

        final int steps = size();
        final int first_step = (int) (alpha_ * steps);
        final int last_step = steps - first_step;
        final int remaining = last_step - first_step;

        if (remaining < 1)
        {
            throw new IllegalArgumentException("Alpha would result in zero steps: " + alpha_);
        }

        if (remaining >= steps)
        {
            return this;
        }

        final double[] eX = new double[remaining];
        final double[] eY = new double[remaining];
        final double[] devX = new double[remaining];
        final double[] devY = new double[remaining];
        final long[] count = new long[remaining];

        for (int i = 0; i < remaining; i++)
        {
            eX[i] = this.getMeanX(first_step + i);
            eY[i] = this.getMeanY(first_step + i);
            devX[i] = this.getDevX(first_step + i);
            devY[i] = this.getDevY(first_step + i);
            count[i] = this.getCount(first_step + i);
        }

        final QuantileDistribution reduced = new QuantileDistribution(eX, eY, devX, devY, count, false);
        return reduced;
    }


    public InterpolatedCurve getCountCurve(final boolean linear_)
    {
        final int size = this.size();
        final double[] countDoubles = new double[size];

        for (int i = 0; i < size; i++)
        {
            countDoubles[i] = (double) this.getCount(i);
        }

        return new InterpolatedCurve(_rQuantile.getX(), countDoubles, linear_, false);
    }

    public InterpolatedCurve getValueCurve(final boolean linear_)
    {
        return new InterpolatedCurve(_rQuantile.getX(), _eY, linear_, false);
    }

    public double getMeanX()
    {
        return _rQuantile.getMeanX();
    }

    public double getMeanY()
    {
        return _meanY;
    }

    public double getDevX()
    {
        return _rQuantile.getDevX();
    }

    public double getDevY()
    {
        return _meanDevY * Math.sqrt(getTotalCount());
    }

    public double getMeanDevX()
    {
        return _rQuantile.getMeanDevX();
    }

    public double getMeanDevY()
    {
        return _meanDevY;
    }

    public long getTotalCount()
    {
        return _rQuantile.getTotalCount();
    }

    public int size()
    {
        return _rQuantile.size();
    }

    public long getCount(final int index_)
    {
        return _rQuantile.getCount(index_);
    }

    public double getMeanX(final int index_)
    {
        return _rQuantile.getMeanX(index_);
    }

    public double getMeanY(final int index_)
    {
        return _eY[index_];
    }

    public double getDevX(final int index_)
    {
        return _rQuantile.getDevX(index_);
    }

    public double getDevY(final int index_)
    {
        return _devY[index_];
    }

}
