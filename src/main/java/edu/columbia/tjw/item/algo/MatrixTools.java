package edu.columbia.tjw.item.algo;

import org.apache.commons.math3.analysis.BivariateFunction;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.function.Add;
import org.apache.commons.math3.analysis.function.Subtract;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;

import java.io.Serializable;

public final class MatrixTools
{
    private static final BivariateFunction ADD = new Add();
    private static final BivariateFunction SUBTRACT = new Subtract();

    private MatrixTools()
    {
    }

    public static DoubleMatrix scalarMultiply(final DoubleMatrix input_, final double scalar_)
    {
        final SerializableUnivariateFunction multiplier = (double x) -> x * scalar_;
        return DoubleMatrix.apply(multiplier, input_);
    }

    public static DoubleMatrix add(final DoubleMatrix a_, final DoubleMatrix b_)
    {
        return DoubleMatrix.apply(ADD, a_, b_);
    }

    public static DoubleMatrix subtract(final DoubleMatrix a_, final DoubleMatrix b_)
    {
        return DoubleMatrix.apply(SUBTRACT, a_, b_);
    }

    public static DoubleMatrix multiplyAccumulate(final DoubleMatrix a_, final DoubleMatrix b_, final double scalar_)
    {
        // This is more efficient, as it avoids one more object creation and indirection.
        final SerializableBivariateFunction mac = (double x, double y) ->
        {
            return x + (y * scalar_);
        };
        return DoubleMatrix.apply(mac, a_, b_);
        //return add(a_, scalarMultiply(b_, scalar_));
    }

    public static RealMatrix toApacheMatrix(final DoubleMatrix a_)
    {
        return new Array2DRowRealMatrix(a_.copyOfUnderlying());
    }


    private interface SerializableUnivariateFunction extends UnivariateFunction, Serializable
    {

    }

    private interface SerializableBivariateFunction extends BivariateFunction, Serializable
    {

    }
}
