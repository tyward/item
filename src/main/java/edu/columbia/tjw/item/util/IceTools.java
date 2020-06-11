package edu.columbia.tjw.item.util;

import edu.columbia.tjw.item.algo.DoubleVector;
import edu.columbia.tjw.item.algo.VectorTools;
import edu.columbia.tjw.item.fit.calculator.BlockResult;
import org.apache.commons.math3.analysis.UnivariateFunction;

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
     * @param iVec     The underlying derivative (already squared if iSquared)
     * @param jVec     The diagonal of the matrix J.
     * @param jWeight  The weight computed for each diagonal. (see computeJWeight)
     * @param iSquared If true, then iVec is already squared, otherwise this function will square it.
     * @return The ice adjustment approximating IJ^{-1}.
     */
    public static double computeIce3Sum(final DoubleVector iVec, final DoubleVector jVec, final DoubleVector jWeight,
                                        final boolean iSquared)
    {
        final int dimension = iVec.getSize();
        final double iTermCutoff;

        if (iSquared)
        {
            iTermCutoff = VectorTools.maxAbsElement(iVec) * EPSILON;
        }
        else
        {
            final double maxVal = VectorTools.maxAbsElement(iVec);
            iTermCutoff = maxVal * maxVal * EPSILON;
        }

        if (iTermCutoff == 0.0)
        {
            return 0.0;
        }

        double iceSum3 = 0.0;

        for (int i = 0; i < dimension; i++)
        {
            // Now square this thing.
            final double iTerm;

            if (iSquared)
            {
                iTerm = iVec.getEntry(i);
            }
            else
            {
                final double raw = iVec.getEntry(i);
                iTerm = raw * raw;
            }

            if (iTerm < iTermCutoff)
            {
                // This particular term is irrelevant, its gradient is basically zero so just skip it.
                continue;
            }

            final double jTerm = jVec.getEntry(i);

            // As we shift this towards the case where J is not positive definite, we start relying more and more
            // heavily on the I term portion, which makes this ratio close to 1.0.
            final double weight = jWeight.getEntry(i);
            final double scaledJ = jTerm * weight;
            final double scaledI = iTerm * (1.0 - weight);
            final double iceTerm3 = iTerm / (scaledJ + scaledI);

            iceSum3 += iceTerm3;
        }

        return iceSum3;
    }

    public static double computeIce3Sum(final BlockResult resultBlock_)
    {
        return computeIce3Sum(resultBlock_.getDerivativeSquared(), resultBlock_.getJDiag(),
                computeJWeight(resultBlock_.getJDiag()), true);
//        final int dimension = resultBlock_.getDerivativeDimension();
//        final double iTermCutoff = computeITermCutoff(resultBlock_);
//
//        if (iTermCutoff == 0.0)
//        {
//            return 0.0;
//        }
//
//        final double jDiagCutoff = computeJDiagCutoff(resultBlock_);
//        double iceSum3 = 0.0;
//
//        for (int i = 0; i < dimension; i++)
//        {
//            final double iTerm = resultBlock_.getD2Entry(i); // Already squared, this one is.
//
//            if (iTerm < iTermCutoff)
//            {
//                // This particular term is irrelevant, its gradient is basically zero so just skip it.
//                continue;
//            }
//
//            final double jTerm = resultBlock_.getJDiagEntry(i);
//
//            // As we shift this towards the case where J is not positive definite, we start relying more and more
//            // heavily on the I term portion, which makes this ratio close to 1.0.
//            final double weight = computeWeight(jTerm, jDiagCutoff);
//            final double scaledJ = jTerm * weight;
//            final double scaledI = iTerm * (1.0 - weight);
//            final double iceTerm3 = iTerm / (scaledJ + scaledI);
//
//            iceSum3 += iceTerm3;
//        }
//
//        return iceSum3;
    }


    public static double[] fillIceExtraDerivative(final BlockResult resultBlock_)
    {
        final int dimension = resultBlock_.getDerivativeDimension();
        final int size = resultBlock_.getSize();
        final double[] extraDerivative = new double[dimension];
        final double iTermCutoff = computeITermCutoff(resultBlock_);

        if (iTermCutoff == 0.0)
        {
            return extraDerivative;
        }
        if (size == 0)
        {
            return extraDerivative;
        }

        // TODO: Fix this, we have no way to get this number here, so hard coding it.
        final double logM = Math.log(3) * size;
        //final double iceBalance = 1.0 / (logM + _params.getEffectiveParamCount());
        final double iceBalance = 1.0 / logM;

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
            final double denominator = (Math.max(jTerm, 0) * (1.0 - iceBalance) + iTerm * iceBalance);

            // Assume that the third (and neglected) term roughly cancels one of these two terms, eliminating
            // the 2.0.
            extraDerivative[i] = numerator / (denominator * size);
        }

        return extraDerivative;
    }

    public static double[] fillIce3ExtraDerivative(final BlockResult resultBlock_)
    {
        final int dimension = resultBlock_.getDerivativeDimension();
        final int size = resultBlock_.getSize();
        final double[] extraDerivative = new double[dimension];
        final double iTermCutoff = computeITermCutoff(resultBlock_);
        final double jDiagCutoff = computeJDiagCutoff(resultBlock_);

        if (iTermCutoff == 0.0 || jDiagCutoff == 0.0)
        {
            return extraDerivative;
        }
        if (size == 0)
        {
            return extraDerivative;
        }

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

        return extraDerivative;
    }

}
