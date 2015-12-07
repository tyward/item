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
import java.io.Serializable;

/**
 *
 * @author tyler
 */
public final class QuantileDistribution implements Serializable
{
    private static final long serialVersionUID = 1116222802982789849L;

    private final double[] _eX;
    private final double[] _devX;
    private final double[] _eY;
    private final double[] _devY;
    private final long[] _count;
    private final long _totalCount;

    //Some stats on the global distribution (across all buckets). 
    private final double _meanX;
    private final double _meanY;
    private final double _meanDevX;
    private final double _meanDevY;

    public QuantileDistribution(final double[] eX_, final double[] eY_, final double[] devX_, final double[] devY_, final long[] count_, final boolean doCopy_)
    {
        final int size = eX_.length;

        if (size != eY_.length || size != devX_.length || size != devY_.length || size != count_.length)
        {
            throw new IllegalArgumentException("Length mismatch.");
        }

        double sumX = 0.0;
        double sumY = 0.0;
        double varX = 0.0;
        double varY = 0.0;
        long count = 0;

        if (doCopy_)
        {
            _eX = eX_.clone();
            _eY = eY_.clone();
            _devX = devX_.clone();
            _devY = devY_.clone();
            _count = count_.clone();
        }
        else
        {
            _eX = eX_;
            _eY = eY_;
            _devX = devX_;
            _devY = devY_;
            _count = count_;
        }

        for (int i = 0; i < size; i++)
        {
            final long bucketCount = _count[i];
            final double termX = _eX[i] * bucketCount;
            final double termY = _eY[i] * bucketCount;
            final double bucketVarX = _devX[i] * _devX[i] * bucketCount;
            final double bucketVarY = _devY[i] * _devY[i] * bucketCount;

            sumX += termX;
            sumY += termY;
            varX += bucketVarX;
            varY += bucketVarY;
            count += bucketCount;
        }

        _meanX = sumX / count;
        _meanY = sumY / count;
        _meanDevX = Math.sqrt(varX) / count;
        _meanDevY = Math.sqrt(varY) / count;
        _totalCount = count;
    }

    public QuantileDistribution(final QuantApprox approx_)
    {
        final int approxSize = approx_.size();

        _eX = new double[approxSize];
        _devX = new double[approxSize];
        _eY = new double[approxSize];
        _devY = new double[approxSize];

        _count = new long[approxSize];

        long totalCount = 0;

        int pointer = 0;

        for (final QuantileNode next : approx_)
        {
            _eX[pointer] = next.getMeanX();
            _devX[pointer] = next.getStdDevX();
            _eY[pointer] = next.getMeanY();
            _devY[pointer] = next.getStdDevY();

            final long count = next.getCount();
            _count[pointer] = count;
            totalCount += count;
            pointer++;
        }

        _totalCount = totalCount;
        _meanX = approx_.getMeanX();
        _meanY = approx_.getMeanY();
        _meanDevX = approx_.getStdDevX();
        _meanDevY = approx_.getStdDevY();
    }

    public double getMeanX()
    {
        return _meanX;
    }

    public double getMeanY()
    {
        return _meanY;
    }

    public double getMeanDevX()
    {
        return _meanDevX;
    }

    public double getMeanDevY()
    {
        return _meanDevY;
    }

    public long getTotalCount()
    {
        return _totalCount;
    }

    public int size()
    {
        return _count.length;
    }

    public long getCount(final int index_)
    {
        return _count[index_];
    }

    public double getMeanX(final int index_)
    {
        return _eX[index_];
    }

    public double getMeanY(final int index_)
    {
        return _eY[index_];
    }

    public double getDevX(final int index_)
    {
        return _devX[index_];
    }

    public double getDevY(final int index_)
    {
        return _devY[index_];
    }

}