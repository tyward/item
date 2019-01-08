package edu.columbia.tjw.item.fit.calculator;

import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.algo.VarianceCalculator;

public final class FitPointAnalyzer<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
{
    // How many blocks do we calculate at a time (to take advantage of threading). This should be several times the number of cores in the computer.
    private static final int DEFAULT_SUPERBLOCK_SIZE = 100;

    private final int _superBlockSize;


    public FitPointAnalyzer()
    {
        _superBlockSize = DEFAULT_SUPERBLOCK_SIZE;
    }

    public double compareEntropies(final FitPoint<S, R, T> a_, final FitPoint<S, R, T> b_)
    {
        return compareEntropies(a_, b_, 0.0);
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
    public double compareEntropies(final FitPoint<S, R, T> a_, final FitPoint<S, R, T> b_, final double minStdDev_)
    {
        if (a_.getBlockCount() != b_.getBlockCount())
        {
            throw new IllegalArgumentException("Incomparable points.");
        }

        if (a_.getNextBlock() > b_.getNextBlock())
        {
            return -1.0 * compareEntropies(b_, a_);
        }

        // b_ has at least as many values as a_
        final VarianceCalculator vcalc = new VarianceCalculator();
        final double sqrtBlockSize = Math.sqrt(a_.getBlockSize());

        for (int i = 0; i < a_.getBlockCount(); i++)
        {
            if (i >= a_.getNextBlock())
            {
                final double dev = vcalc.getDev() * sqrtBlockSize;

                if (Math.abs(dev) >= minStdDev_)
                {
                    // We are done, no further calculations needed.
                    break;
                }

                final int target = Math.min(i + _superBlockSize, a_.getBlockCount());
                a_.computeUntil(target);
                b_.computeUntil(target);
                // OK, carry on now that data has been computed.
            }

            final BlockResult a = a_.getBlock(i);
            final BlockResult b = b_.getBlock(i);

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

        if (a_.getNextBlock() < 1)
        {
            // No data, can't tell which is better.
            return 0.0;
        }

        final double meanDiff = vcalc.getMean();
        double dev = sqrtBlockSize * vcalc.getDev();

        if (vcalc.getCount() < 3 || 0.0 == dev)
        {
            // Special case, can't do much else about the variance.
            // This is a considerable overestimate.
            final double devA = a_.getBlock(0).getEntropyMeanDev();
            final double devB = b_.getBlock(0).getEntropyMeanDev();
            dev = 0.5 * (devA + devB);
        }

        final double zScore = meanDiff / dev;
        return zScore;
    }


}
