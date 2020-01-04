package edu.columbia.tjw.item.fit.calculator;

import edu.columbia.tjw.item.algo.VarianceCalculator;

public final class FitPointAnalyzer
{
    private final int _superBlockSize;
    private final double _minStdDev;

    public FitPointAnalyzer(final int superBlockSize_, final double minStdDev_)
    {
        _superBlockSize = superBlockSize_;
        _minStdDev = minStdDev_;
    }

    public double compare(final FitPoint a_, final FitPoint b_)
    {
        return compare(a_, b_, _minStdDev);
    }

    public double getSigmaTarget()
    {
        return _minStdDev;
    }

    public double[] getDerivative(final FitPoint point_)
    {
        point_.computeAll(BlockCalculationType.FIRST_DERIVATIVE);
        final BlockResult aggregated = point_.getAggregated(BlockCalculationType.FIRST_DERIVATIVE);
        return aggregated.getDerivative();
    }

    public double computeObjective(final FitPoint point_, final int endBlock_)
    {
        point_.computeUntil(endBlock_, BlockCalculationType.VALUE);
        final BlockResult aggregated = point_.getAggregated(BlockCalculationType.VALUE);
        return aggregated.getEntropyMean();
    }

    public double computeObjectiveStdDev(final FitPoint point_, final int endBlock_)
    {
        return point_.getObjectiveStdDev(endBlock_);
    }


    /**
     * How many sigma higher/lower than other_ is this block result. If positive, this is X sigma higher than other_.
     * <p>
     * N.B: This is calculated based on the differences. Merely having a high std. dev. will not make much
     * difference, it's about how consistently one-sided the differences are.
     * <p>
     * continue calculating until data runs out or the given stdDev has been hit.
     *
     * @param a_
     * @param b_
     * @return
     */
    public double compare(final FitPoint a_, final FitPoint b_, final double minStdDev_)
    {
        if (a_ == b_)
        {
            // These are identical objects, the answer would always be zero.
            return 0.0;
        }
        if (a_.getBlockCount() != b_.getBlockCount())
        {
            throw new IllegalArgumentException("Incomparable points.");
        }

        if (a_.getNextBlock(BlockCalculationType.VALUE) > b_.getNextBlock(BlockCalculationType.VALUE))
        {
            return -1.0 * compare(b_, a_, minStdDev_);
        }

        final BlockCalculationType valType = BlockCalculationType.VALUE;

        // b_ has at least as many values as a_
        final VarianceCalculator vcalc = new VarianceCalculator();
        final double sqrtBlockSize = Math.sqrt(a_.getBlockSize());

        for (int i = 0; i < a_.getBlockCount(); i++)
        {
            if (i >= a_.getNextBlock(valType))
            {
                if (i >= _superBlockSize && vcalc.getMean() != 0.0)
                {
                    // Only look at the std. dev. if i is large enough, and we've seen at
                    // least a few differences so far.
                    final double dev = vcalc.getDev() * sqrtBlockSize;

                    if (Math.abs(dev) >= minStdDev_)
                    {
                        // We are done, no further calculations needed.
                        break;
                    }
                }

                final int target = Math.min(i + _superBlockSize, a_.getBlockCount());
                a_.computeUntil(target, valType);
                b_.computeUntil(target, valType);
                // OK, carry on now that data has been computed.
            }

            final BlockResult a = a_.getBlock(i, valType);
            final BlockResult b = b_.getBlock(i, valType);

            if (a.getRowStart() != b.getRowStart())
            {
                throw new IllegalArgumentException("Misaligned blocks.");
            }
            if (a.getRowEnd() != b.getRowEnd())
            {
                throw new IllegalArgumentException("Misaligned blocks.");
            }

            final double aMean = a.getEntropyMean();
            final double bMean = b.getEntropyMean();
            final double diff = (aMean - bMean);
            vcalc.update(diff);
        }

        if (a_.getNextBlock(valType) < 1)
        {
            // No data, can't tell which is better.
            return 0.0;
        }

        final double meanDiff = vcalc.getMean();
        double dev = vcalc.getMeanDev();

        if (vcalc.getCount() < 3 || 0.0 == dev)
        {
            // Special case, can't do much else about the variance.
            // This is a considerable overestimate.
            final double devA = a_.getBlock(0, valType).getEntropyMeanDev();
            final double devB = b_.getBlock(0, valType).getEntropyMeanDev();
            dev = 0.5 * (devA + devB);
        }

        final double zScore = meanDiff / dev;
        return zScore;
    }


}
