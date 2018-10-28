package edu.columbia.tjw.item.fit.calculator;

import edu.columbia.tjw.item.algo.VarianceCalculator;

import java.util.ArrayList;
import java.util.List;

public final class BlockResultCompound
{
    private BlockResult _aggregated;
    private int _nextStart;

    private final List<BlockResult> _results;

    public BlockResultCompound()
    {
        _results = new ArrayList<>();
        _nextStart = 0;
    }

    public void appendResult(final BlockResult next_)
    {
        if (null == next_)
        {
            throw new NullPointerException("Next cannot be null.");
        }

        if (next_.getRowStart() != _nextStart)
        {
            throw new IllegalArgumentException("Blocks must be added in order.");
        }

        _nextStart = next_.getRowEnd();
        _results.add(next_);

        _aggregated = null;
    }

    public BlockResult getAggregated()
    {
        if (null == _aggregated)
        {
            _aggregated = new BlockResult(_results);
        }

        return _aggregated;
    }

    public int getNextStart()
    {
        return _nextStart;
    }

    public int getBlockCount()
    {
        return _results.size();
    }

    public BlockResult getBlock(final int index_)
    {
        return _results.get(index_);
    }


    /**
     * How many sigma higher/lower than other_ is this block result. If positive, this is X sigma higher than other_.
     * <p>
     * N.B: This is calculated based on the differences. Merely having a high std. dev. will not make much
     * difference, it's about how consistently one-sided the differences are.
     *
     * @param that_
     * @return
     */
    public double compareEntropies(final BlockResultCompound that_)
    {
        final int compareSize = Math.min(this.getBlockCount(), that_.getBlockCount());

        if (compareSize < 1)
        {
            // No data, can't tell which is better.
            return 0.0;
        }

        final VarianceCalculator vcalc = new VarianceCalculator();
        double sum = 0.0;
        double s2 = 0.0;
        double count = 0.0;

        for (int i = 0; i < compareSize; i++)
        {
            final BlockResult a = this.getBlock(i);
            final BlockResult b = that_.getBlock(i);

            if (a.getRowStart() != b.getRowStart())
            {
                throw new IllegalArgumentException("Misaligned blocks.");
            }
            if (a.getRowEnd() != b.getRowEnd())
            {
                throw new IllegalArgumentException("Misaligned blocks.");
            }

            count += a.getSize();
            final double aMean = a.getEntropyMean();
            final double bMean = b.getEntropyMean();
            final double diff = (aMean - bMean);
            vcalc.update(diff);
        }

        final double meanDiff = vcalc.getMean();
        final double var = vcalc.getVariance();
        final double dev;

        if (compareSize < 3 || 0.0 == var)
        {
            // Special case, can't do much else about the variance.
            // This is a considerable overestimate.
            final double devA = this.getBlock(0).getEntropyMeanDev();
            final double devB = that_.getBlock(0).getEntropyMeanDev();
            dev = 0.5 * (devA + devB);
        }
        else
        {

            // Under the assumption that the blocks are mostly the same size, the actual std. dev. of the individual
            // items should be the std. dev. of the block means multiplied by sqrt(blocksize).
            final double blockSize = count / compareSize;
            dev = Math.sqrt(var * blockSize);
        }

        final double zScore = meanDiff / dev;
        return zScore;
    }


}
