package edu.columbia.tjw.item.util;

import edu.columbia.tjw.item.algo.DoubleVector;
import edu.columbia.tjw.item.algo.VectorTools;
import edu.columbia.tjw.item.fit.calculator.BlockResult;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.function.Multiply;

public final class IceTools
{
    public static final double EPSILON = Math.ulp(4.0); // Just a bit bigger than machine epsilon.
    public static final double SQRT_EPSILON = Math.sqrt(EPSILON);

    // Compute exp(-cutoff_/jTerm_), where here x = a/b, and it's zero when x <= 0.0
    public static double computeWeight(final double jTerm_, final double cutoff_)
    {
        if (!(jTerm_ > 0.0))
        {
            return 0.0;
        }
        if (!(cutoff_ > 0.0))
        {
            throw new IllegalArgumentException("Invalid Cutoff term.");
        }

        // x = 1 when jTerm_ == cutoff.
        final double ratio = cutoff_ / jTerm_;
        final double weight = Math.exp(-ratio);
        return weight;
    }

    public static double computeJDiagCutoff(final DoubleVector jDiag)
    {
        final double jMax = VectorTools.maxAbsElement(jDiag);

        // This one is not a square, so use SQRT_EPSILON for the cutoff..
        final double jTermCutoff = jMax * SQRT_EPSILON;
        return jTermCutoff;
    }

    public static double computeJDiagCutoff(final BlockResult resultBlock_)
    {
        return computeJDiagCutoff(resultBlock_.getJDiag());
    }

    public static double computeITermCutoff(final DoubleVector derivativeSquared_)
    {
        final double iTermMax = VectorTools.maxAbsElement(derivativeSquared_);
        final double iTermCutoff = iTermMax * EPSILON;
        return iTermCutoff;
    }

    public static double computeITermCutoff(final BlockResult resultBlock_)
    {
        return computeITermCutoff(resultBlock_.getDerivativeSquared());
    }

    public static double computeIceSum(final DoubleVector derivativeSquared_, final DoubleVector jDiag_)
    {
        final double iTermCutoff = computeITermCutoff(derivativeSquared_);

        if (iTermCutoff == 0.0)
        {
            return 0.0;
        }

        double iceSum = 0.0;

        for (int i = 0; i < derivativeSquared_.getSize(); i++)
        {
            final double iTerm = derivativeSquared_.getEntry(i);

            if (iTerm < iTermCutoff)
            {
                // This particular term is irrelevant, its gradient is basically zero so just skip it.
                continue;
            }

            final double jTerm = jDiag_.getEntry(i);
            final double iceTerm = iTerm / jTerm;
            iceSum += iceTerm;
        }

        return iceSum;
    }

    public static double computeIceSum(final BlockResult resultBlock_)
    {
        return computeIceSum(resultBlock_.getDerivativeSquared(), resultBlock_.getJDiag());
    }

    public static double computeIce2Sum(final DoubleVector derivativeSquared_, final DoubleVector jDiag_,
                                        final int resultCount_)
    {
        final double iTermCutoff = computeITermCutoff(derivativeSquared_);

        if (iTermCutoff == 0.0)
        {
            return 0.0;
        }

        // This is basically the worst an entropy could ever be with a uniform model. It is a reasonable
        // level of
        // "an entropy bad enough that any realistic model should avoid it like the plague, but not so bad that
        // it causes any sort of numerical issues", telling the model that J must be pos. def.

        final int dimension = derivativeSquared_.getSize();

        // TODO: Fix this, we have no way to get this number here, so hard coding it.
        final double logM = Math.log(3) * resultCount_;
        final double iceBalance = 1.0 / logM;

        double iceSum2 = 0.0;

        for (int i = 0; i < dimension; i++)
        {
            final double iTerm = derivativeSquared_.getEntry(i); // Already squared, this one is.

            if (iTerm < iTermCutoff)
            {
                // This particular term is irrelevant, its gradient is basically zero so just skip it.
                continue;
            }

            final double jTerm = jDiag_.getEntry(i);
            final double iceTerm2 = iTerm / (Math.abs(jTerm) * (1.0 - iceBalance) + iTerm * iceBalance);

            iceSum2 += iceTerm2;
        }

        return iceSum2;
    }

    public static double computeIce2Sum(final BlockResult resultBlock_)
    {
        return computeIce2Sum(resultBlock_.getDerivativeSquared(), resultBlock_.getJDiag(), resultBlock_.getSize());
    }

    public static DoubleVector computeJWeight(final DoubleVector jVec)
    {
        final double jTermCutoff = VectorTools.maxAbsElement(jVec) * SQRT_EPSILON;
        final UnivariateFunction func = (x) -> computeWeight(x, jTermCutoff);
        return DoubleVector.apply(func, jVec);
    }


    public static double computeIce3Sum(final double[] iVec, final DoubleVector jVec, final DoubleVector jWeight,
                                        final boolean iSquared)
    {
        return computeIce3Sum(DoubleVector.of(iVec, false), jVec, jWeight, iSquared);
    }

    /**
     * @param gradInput The underlying derivative (already squared if iSquared)
     * @param jVec      The diagonal of the matrix J.
     * @param jWeight   The weight computed for each diagonal. (see computeJWeight)
     * @param isSquared If true, then iVec is already squared, otherwise this function will square it.
     * @return The ice adjustment approximating IJ^{-1}.
     */
    public static double computeIce3Sum(final DoubleVector gradInput, final DoubleVector jVec,
                                        final DoubleVector jWeight,
                                        final boolean isSquared)
    {
        final DoubleVector iVec;

        if (isSquared)
        {
            iVec = gradInput;
        }
        else
        {
            iVec = DoubleVector.apply(new Multiply(), gradInput, gradInput);
        }


        final int dimension = iVec.getSize();
        final double iTermCutoff = computeITermCutoff(iVec);

        if (iTermCutoff == 0.0)
        {
            return 0.0;
        }

        double iceSum3 = 0.0;

        for (int i = 0; i < dimension; i++)
        {
            final double iTerm = iVec.getEntry(i);

            if (iTerm < iTermCutoff)
            {
                continue;
            }

            final double jTerm = jVec.getEntry(i);

            // As we shift this towards the case where J is not positive definite, we start relying more and more
            // heavily on the I term portion, which makes this ratio close to 1.0.
            final double weight = jWeight.getEntry(i);
            final double iceTerm3 = computeTermRatio(iTerm, jTerm, weight);

            iceSum3 += iceTerm3;
        }

        return iceSum3;
    }

    /**
     * This is the core of the algorithm, computing a single element of vD^{-1}v
     * <p>
     * This can be unstable if iTerm is very small, so make sure to check that first.
     *
     * @param iTerm
     * @param jTerm
     * @param jWeight
     * @return
     */
    public static double computeTermRatio(final double iTerm, final double jTerm,
                                          final double jWeight)
    {
        final double scaledJ = jTerm * jWeight;
        final double scaledI = iTerm * (1.0 - jWeight);
        final double iceTerm3 = iTerm / (scaledJ + scaledI);
        return iceTerm3;
    }


    public static double computeIce3Sum(final BlockResult resultBlock_)
    {
        return computeIce3Sum(resultBlock_.getDerivativeSquared(), resultBlock_.getJDiag(),
                computeJWeight(resultBlock_.getJDiag()), true);
    }


    public static DoubleVector fillIceExtraDerivative(final DoubleVector derivativeSquared_,
                                                      final DoubleVector shiftedGradient_,
                                                      final DoubleVector jDiag_, final int blockSize_)
    {
        final int dimension = derivativeSquared_.getSize();

        final double iTermCutoff = computeITermCutoff(derivativeSquared_);

        if (iTermCutoff == 0.0)
        {
            return DoubleVector.constantVector(0, dimension);
        }
        if (blockSize_ == 0)
        {
            return DoubleVector.constantVector(0, dimension);
        }

        final double[] extraDerivative = new double[dimension];

        // TODO: Fix this, we have no way to get this number here, so hard coding it.
        final double logM = Math.log(3) * blockSize_;
        //final double iceBalance = 1.0 / (logM + _params.getEffectiveParamCount());
        final double iceBalance = 1.0 / logM;

        for (int i = 0; i < dimension; i++)
        {
            final double numerator = shiftedGradient_.getEntry(i);

            if (numerator < iTermCutoff)
            {
                // The adjustment here is zero....
                continue;
            }

            final double iTerm = derivativeSquared_.getEntry(i); // Already squared, this one is.

            if (iTerm < iTermCutoff)
            {
                // This particular term is irrelevant, its gradient is basically zero so just skip it.
                continue;
            }

            final double jTerm = jDiag_.getEntry(i);
            final double denominator = (Math.max(jTerm, 0) * (1.0 - iceBalance) + iTerm * iceBalance);

            // Assume that the third (and neglected) term roughly cancels one of these two terms, eliminating
            // the 2.0.
            extraDerivative[i] = numerator / (denominator * blockSize_);
        }

        return DoubleVector.of(extraDerivative, false);
    }

    public static DoubleVector fillIceExtraDerivative(final BlockResult resultBlock_)
    {
        return fillIceExtraDerivative(resultBlock_.getDerivativeSquared(), resultBlock_.getShiftGradient(),
                resultBlock_.getJDiag(), resultBlock_.getSize());
    }


    public static DoubleVector fillIceStableBExtraDerivative(final BlockResult resultBlock_)
    {
        final int dimension = resultBlock_.getDerivativeDimension();
        final int size = resultBlock_.getSize();

        final double iTermCutoff = computeITermCutoff(resultBlock_);
        final double jDiagCutoff = computeJDiagCutoff(resultBlock_);

        if (iTermCutoff == 0.0 || jDiagCutoff == 0.0)
        {
            return DoubleVector.constantVector(0, dimension);
        }
        if (size == 0)
        {
            return DoubleVector.constantVector(0, dimension);
        }

        final double[] extraDerivative = new double[dimension];

        for (int i = 0; i < dimension; i++)
        {
            final double numerator = resultBlock_.getShiftGradientEntry(i);

            if (numerator < iTermCutoff)
            {
                // The adjustment here is zero....
                continue;
            }

            final double iTerm = resultBlock_.getD2Entry(i); // Already squared, this one is.

            if (iTerm < iTermCutoff)
            {
                // This particular term is irrelevant, its gradient is basically zero so just skip it.
                continue;
            }

            final double jTerm = resultBlock_.getJDiagEntry(i);

            // As we shift this towards the case where J is not positive definite, we start relying more and more
            // heavily on the I term portion, which makes this ratio close to 1.0.
            final double weight = computeWeight(jTerm, jDiagCutoff);
            final double scaledJ = jTerm * weight;
            final double scaledI = iTerm * (1.0 - weight);

            final double denominator = (scaledJ + scaledI);

            // Assume that the third (and neglected) term roughly cancels one of these two terms, eliminating
            // the 2.0.
            extraDerivative[i] = numerator / (denominator * size);
        }

        return DoubleVector.of(extraDerivative, false);
    }

}
