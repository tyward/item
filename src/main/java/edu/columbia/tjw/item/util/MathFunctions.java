/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.columbia.tjw.item.util;

import org.apache.commons.math3.util.FastMath;

/**
 *
 * @author tyler
 */
public final class MathFunctions
{
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

}
