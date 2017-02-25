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

import org.apache.commons.math3.util.FastMath;

/**
 *
 * @author tyler
 */
public final class MathFunctions
{
    // SQRT of the difference between 1.0 and the smallest value larger than 1.0.
    private static final double SQRT_EPSILON = Math.sqrt(Math.ulp(1.0));

    private MathFunctions()
    {
    }

    //Compute the binary logistic of the input_.
    public static double logisticFunction(final double input_)
    {
        final double exp = FastMath.exp(-input_);
        final double result = 1.0 / (1.0 + exp);
        return result;
    }

    public static double logitFunction(final double input_)
    {
        final double ratio = input_ / (1.0 - input_);
        final double output = Math.log(ratio);
        return output;
    }

    public static boolean isAicBetter(final double starting_, final double ending_)
    {
        //Seems fairly obvious.
        return isAicWorse(ending_, starting_);
    }

    /**
     * Is this AIC worse, beyond the minor jitter that can come from
     * non-commutative behavior of floating point math.
     *
     * @param starting_
     * @param ending_
     * @return
     */
    public static boolean isAicWorse(final double starting_, final double ending_)
    {
        if (!(starting_ > 0.0) || Double.isInfinite(starting_))
        {
            throw new IllegalArgumentException("Invalid starting value.");
        }
        if (!(ending_ > 0.0) || Double.isInfinite(ending_))
        {
            throw new IllegalArgumentException("Invalid ending value.");
        }

        //The change in the AIC. 
        final double diff = ending_ - starting_;
        final double norm = ending_ + starting_;
        final double relDiff = diff / norm;

        final boolean output = relDiff > SQRT_EPSILON;
        return output;
    }

}
