/*
 * Copyright 2017 tyler.
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
 */
package edu.columbia.tjw.item.util;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;

import java.util.Random;

/**
 * @author tyler
 */
public final class MathTools
{
    public static final double EPSILON = Math.ulp(1.0);
    public static final double SQRT_EPSILON = Math.sqrt(EPSILON);


    private MathTools()
    {
    }

    public static double estimateDot(final RealMatrix cov1_, final RealMatrix cov2_, final Random rand_)
    {
        final int dimension = cov1_.getRowDimension();
        double sumX = 0.0;
        double sumX2 = 0.0;

        final RealMatrix a = new Array2DRowRealMatrix(1, dimension);
        final RealMatrix b = new Array2DRowRealMatrix(1, dimension);
        final int limit = 200;

        for (int i = 0; i < limit; i++)
        {
            for (int k = 0; k < dimension; k++)
            {
                a.setEntry(0, k, rand_.nextGaussian());
                b.setEntry(0, k, rand_.nextGaussian());
            }

            final RealMatrix ta = cov1_.multiply(a.transpose());
            final RealMatrix tb = cov2_.multiply(b.transpose());

            final RealMatrix dot = ta.transpose().multiply(tb);

            final double dotVal = dot.getEntry(0, 0);

            sumX += dotVal;
            sumX2 += (dotVal * dotVal);

            final double eX = sumX / (i + 1);
            final double eX2 = sumX2 / (i + 1);
            final double varX = eX2 - (eX * eX);

            if (varX <= 0.0)
            {
                continue;
            }

            final double muDev = Math.sqrt(varX / (i + 1));

            if (Math.abs(eX) > 4.0 * muDev)
            {
                return eX;
            }

            if (i == limit - 1)
            {
                return eX;
            }

        }

        //Not reachable.
        return Double.NaN;
    }

    public static RealMatrix pseudoInverse(final RealMatrix matrix_)
    {
        final SingularValueDecomposition decomp = new SingularValueDecomposition(matrix_);

        final RealMatrix s = decomp.getS();

        final double[] vals = decomp.getSingularValues();
        final double max = vals[0];
        final double cutoff = (1.0e-8) * max;

        final RealMatrix diag = new Array2DRowRealMatrix(vals.length, vals.length);

        for (int i = 0; i < vals.length; i++)
        {
            if (vals[i] > cutoff)
            {
                diag.setEntry(i, i, 1.0 / vals[i]);
            }
            else
            {
                diag.setEntry(i, i, 0.0);
            }
        }

        final RealMatrix inverse = decomp.getV().multiply(diag).multiply(decomp.getUT());
        return inverse;
    }

    public static double matrixNorm(final RealMatrix matrix_)
    {
        final SingularValueDecomposition decomp = new SingularValueDecomposition(matrix_);
        return decomp.getSingularValues()[0];
    }

    public static double dot(final double[] a_, final double[] b_)
    {
        double dot = 0.0;

        for (int i = 0; i < a_.length; i++)
        {
            dot += a_[i] * b_[i];
        }

        return dot;
    }

    public static double maxAbsElement(final double[] x_)
    {
        double maxAbs = 0.0;

        for (int i = 0; i < x_.length; i++)
        {
            maxAbs = Math.max(maxAbs, Math.abs(x_[i]));
        }

        return maxAbs;
    }

    public static double magnitude(final double[] x_)
    {
        final double dot = dot(x_, x_);
        final double output = Math.sqrt(dot);
        return output;
    }

    public static double cos(final double[] a_, final double[] b_)
    {
        final double dot = dot(a_, b_);
        final double magA = magnitude(a_);
        final double magB = magnitude(b_);
        final double cos = dot / (magA * magB);
        return cos;
    }

    /**
     * returns (a-b).
     *
     * @param a_
     * @param b_
     * @return
     */
    public static final double[] subtract(final double[] a_, final double[] b_)
    {
        final int dimension = a_.length;

        if (b_.length != dimension)
        {
            throw new IllegalArgumentException("Dimension mismatch.");
        }

        final double[] c = new double[dimension];

        for (int i = 0; i < dimension; i++)
        {
            c[i] = a_[i] - b_[i];
        }

        return c;
    }

}
