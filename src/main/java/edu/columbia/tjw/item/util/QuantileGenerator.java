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
package edu.columbia.tjw.item.util;

import edu.columbia.tjw.item.ItemRegressorReader;
import edu.columbia.tjw.item.algo.QuantApprox;
import edu.columbia.tjw.item.algo.QuantApprox.QuantileNode;
import edu.columbia.tjw.item.data.InterpolatedCurve;

/**
 *
 * @author tyler
 */
public final class QuantileGenerator
{
    private static final int BLOCK_SIZE = 10 * 1000;
    private static final double SIGMA_LIMIT = 3;

    private final QuantApprox _approx;

    private final double[] _eX;
    private final double[] _eY;
    private final double[] _varY;
    private final boolean _varTestPassed;
    private final InterpolatedCurve _curve;

    public QuantileGenerator(final ItemRegressorReader xReader_, final ItemRegressorReader yReader_)
    {
        this(xReader_, yReader_, QuantApprox.DEFAULT_BUCKETS);
    }

    public QuantileGenerator(final ItemRegressorReader xReader_, final ItemRegressorReader yReader_, final int bucketCount_)
    {
        _approx = new QuantApprox(bucketCount_, QuantApprox.DEFAULT_LOAD);

        final int size = xReader_.size();

        if (yReader_.size() != size)
        {
            throw new IllegalArgumentException("Size mismatch.");
        }

        boolean passes = false;
        final double limit2 = SIGMA_LIMIT * SIGMA_LIMIT;

        //We will change these sizes later.
        double[] eX = new double[0];
        double[] eY = eX;
        double[] varY = eX;

        for (int i = 0; i < size; i++)
        {
            final double x = xReader_.asDouble(i);
            final double y = yReader_.asDouble(i);

            _approx.addObservation(x, y, true);

            if (0 == (i + 1) % BLOCK_SIZE)
            {
                int index = 0;
                final int approxSize = _approx.size();

                if (eX.length != approxSize)
                {
                    eX = new double[approxSize];
                    eY = new double[approxSize];
                    varY = new double[approxSize];
                }

                //let's check out the variance info. 
                for (final QuantileNode next : _approx)
                {
                    eX[index] = next.getEX();
                    eY[index] = next.getEY();
                    varY[index] = next.getVarY();
                    index++;
                }

                passes = true;

                for (int w = 1; w < _approx.size(); w++)
                {
                    final double ya = eY[w - 1];
                    final double yb = eY[w];
                    final double va = varY[w - 1];
                    final double vb = varY[w];

                    final double diff = (ya - yb);
                    final double d2 = diff * diff;

                    //Same as saying that the root squared diff is greater than several sigma. 
                    //i.e. we pulled in enough data that we can distinguish between these buckets. 
                    if (d2 < (limit2 * va) || d2 < (limit2 * vb))
                    {
                        passes = false;
                        break;
                    }
                }

                if (passes)
                {
                    break;
                }
            }
        }

        _eX = eX;
        _eY = eY;
        _varY = varY;

        _varTestPassed = passes;
        _curve = new InterpolatedCurve(_eX, _eY, true, false);
    }

    public boolean getVarTestPassed()
    {
        return _varTestPassed;
    }

    public InterpolatedCurve getQuantileCurve()
    {
        return _curve;
    }

    public int size()
    {
        return _eX.length;
    }

    public double getVarY(final int index_)
    {
        return _varY[index_];
    }

    public double getStdDevY(final int index_)
    {
        return Math.sqrt(getVarY(index_));
    }

}
