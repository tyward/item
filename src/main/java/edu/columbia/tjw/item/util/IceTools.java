package edu.columbia.tjw.item.util;

import edu.columbia.tjw.item.fit.calculator.BlockResult;

public final class IceTools
{
    public static final double EPSILON = Math.ulp(4.0); // Just a bit bigger than machine epsilon.
    public static final double SQRT_EPSILON = Math.sqrt(EPSILON);

    public static double computeITermCutoff(final BlockResult resultBlock_)
    {
        final int dimension = resultBlock_.getDerivativeDimension();
        double iTermMax = 0.0;

        for (int i = 0; i < dimension; i++)
        {
            iTermMax = Math.max(iTermMax, Math.abs(resultBlock_.getD2Entry(i)));
        }

        final double iTermCutoff = iTermMax * EPSILON;
        return iTermCutoff;
    }

    public static double computeIceSum(final BlockResult resultBlock_)
    {
        final int dimension = resultBlock_.getDerivativeDimension();
        final double iTermCutoff = computeITermCutoff(resultBlock_);

        if (iTermCutoff == 0.0)
        {
            return 0.0;
        }

        double iceSum = 0.0;

        for (int i = 0; i < dimension; i++)
        {
            final double iTerm = resultBlock_.getD2Entry(i); // Already squared, this one is.

            if (iTerm < iTermCutoff)
            {
                // This particular term is irrelevant, its gradient is basically zero so just skip it.
                continue;
            }

            final double jTerm = resultBlock_.getJDiagEntry(i);
            final double iceTerm = iTerm / jTerm;
            iceSum += iceTerm;
        }

        return iceSum;
    }


    public static double computeIce2Sum(final BlockResult resultBlock_)
    {
        final int dimension = resultBlock_.getDerivativeDimension();
        final double iTermCutoff = computeITermCutoff(resultBlock_);

        if (iTermCutoff == 0.0)
        {
            return 0.0;
        }

        // This is basically the worst an entropy could ever be with a uniform model. It is a reasonable
        // level of
        // "an entropy bad enough that any realistic model should avoid it like the plague, but not so bad that
        // it causes any sort of numerical issues", telling the model that J must be pos. def.


        // TODO: Fix this, we have no way to get this number here, so hard coding it.
        final double logM = Math.log(3) * resultBlock_.getSize();
        final double iceBalance = 1.0 / logM;

        double iceSum2 = 0.0;

        for (int i = 0; i < dimension; i++)
        {
            final double iTerm = resultBlock_.getD2Entry(i); // Already squared, this one is.

            if (iTerm < iTermCutoff)
            {
                // This particular term is irrelevant, its gradient is basically zero so just skip it.
                continue;
            }

            final double jTerm = resultBlock_.getJDiagEntry(i);
            final double iceTerm2 = iTerm / (Math.abs(jTerm) * (1.0 - iceBalance) + iTerm * iceBalance);

            iceSum2 += iceTerm2;
        }

        return iceSum2;
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

}
