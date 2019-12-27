package edu.columbia.tjw.item.fit.calculator;

import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemParameters;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.algo.VarianceCalculator;
import edu.columbia.tjw.item.fit.PackedParameters;
import edu.columbia.tjw.item.util.thread.GeneralTask;
import edu.columbia.tjw.item.util.thread.GeneralThreadPool;

import java.util.ArrayList;
import java.util.List;

public final class ItemFitPoint<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
        implements FitPoint
{
    private static final GeneralThreadPool POOL = GeneralThreadPool.singleton();

    private final List<BlockResultCalculator<S, R, T>> _blockCalculators;
    private final PackedParameters<S, R, T> _packed;
    private final ItemParameters<S, R, T> _params;
    private final int _blockSize;
    private final int _totalSize;

    private final BlockResultCompound[] _compound;
    private int[] _nextBlock;

    public ItemFitPoint(final FitPointGenerator<S, R, T> calculator_, final PackedParameters<S, R, T> packed_)
    {
        if (null == calculator_)
        {
            throw new NullPointerException("Calculator cannot be null.");
        }
        if (null == packed_)
        {
            throw new NullPointerException("Packed cannot be null.");
        }

        _blockCalculators = calculator_.getCalculators();
        _packed = packed_.clone(); // May be able to avoid, for now, for safety.
        _params = _packed.generateParams();
        _blockSize = calculator_.getBlockSize();
        _totalSize = calculator_.getRowCount();

        _nextBlock = new int[BlockCalculationType.getValueCount()];
        _compound = new BlockResultCompound[BlockCalculationType.getValueCount()];

        for (int i = 0; i < _compound.length; i++)
        {
            _compound[i] = new BlockResultCompound();
        }

    }

    @Override
    public int getBlockSize()
    {
        return _blockSize;
    }

    @Override
    public int getBlockCount()
    {
        return _blockCalculators.size();
    }

    @Override
    public int getNextBlock(BlockCalculationType type_)
    {
        return _nextBlock[type_.ordinal()];
    }

    @Override
    public void computeAll(BlockCalculationType type_)
    {
        computeUntil(getBlockCount(), type_);
    }

    @Override
    public BlockResult getAggregated(BlockCalculationType type_)
    {
        return _compound[type_.ordinal()].getAggregated();
    }

    @Override
    public void computeUntil(final int endBlock_, BlockCalculationType type_)
    {
        final int nextBlock = getNextBlock(type_);
        final int neededBlocks = endBlock_ - nextBlock;

        if (neededBlocks <= 0)
        {
            return;
        }

        final List<EntropyRunner> runners = new ArrayList<>(neededBlocks);

        for (int i = nextBlock; i < endBlock_; i++)
        {
            final BlockResultCalculator<S, R, T> calc = _blockCalculators.get(i);
            final EntropyRunner runner = new EntropyRunner(calc, _params, type_);
            runners.add(runner);
        }

        final List<BlockResult> analysis = POOL.runAll(runners);
        final BlockResultCompound target = _compound[type_.ordinal()];

        for (final BlockResult result : analysis)
        {
            final int blockIndex = target.getBlockCount();

            for (int k = 0; k < type_.ordinal(); k++)
            {
                if (_compound[k].getBlockCount() > target.getBlockCount())
                {
                    // The new block replaces the previous one, as it has more information.
                    if (_compound[k].getBlock(blockIndex).getEntropyMean() != result.getEntropyMean())
                    {
                        // This should not be possible. We computed an exact replacement block with more information,
                        // and the info in common doesn't match exactly.
                        throw new IllegalStateException("Block mismatch!");
                    }

                    _compound[k].setResult(blockIndex, result);
                }
                else
                {
                    _compound[k].appendResult(result);
                }
            }

            target.appendResult(result);
        }

        // Computing at one level implies computation at all lower levels. For instance, a gradient
        // implies a value as well.
        for (int i = 0; i <= type_.ordinal(); i++)
        {
            _nextBlock[i] = endBlock_;
        }
    }

    @Override
    public BlockResult getBlock(final int index_, BlockCalculationType type_)
    {
        return _compound[type_.ordinal()].getBlock(index_);
    }

    @Override
    public double getMean(int boundary_)
    {
        if (boundary_ == 0)
        {
            return 0.0;
        }
        this.computeUntil(boundary_, BlockCalculationType.VALUE);

        final VarianceCalculator vcalc = new VarianceCalculator();

        for (int i = 0; i < boundary_; i++)
        {
            final double next = getBlock(i, BlockCalculationType.VALUE).getEntropyMean();
            vcalc.update(next);
        }

        final double mean = vcalc.getMean();
        return mean;
    }

    @Override
    public double getStdDev(int boundary_)
    {
        // TODO: Is this right, shouldn't we make sure the counts line up first?
        if (boundary_ == 0)
        {
            return 0.0;
        }
        this.computeUntil(boundary_, BlockCalculationType.VALUE);

        final VarianceCalculator vcalc = new VarianceCalculator();

        for (int i = 0; i < boundary_; i++)
        {
            final double next = getBlock(i, BlockCalculationType.VALUE).getEntropyMean();
            vcalc.update(next);
        }

        final double stdev = vcalc.getMeanDev();
        return stdev;
    }

    public int getSize()
    {
        return this._totalSize;
    }


    private final class EntropyRunner extends GeneralTask<BlockResult>
    {
        private final BlockResultCalculator<S, R, T> _calc;
        private final ItemParameters<S, R, T> _params;
        private final BlockCalculationType _type;

        public EntropyRunner(final BlockResultCalculator<S, R, T> calc_,
                             final ItemParameters<S, R, T> params_,
                             final BlockCalculationType type_)
        {
            _calc = calc_;
            _params = params_;
            _type = type_;
        }


        @Override
        protected BlockResult subRun() throws Exception
        {
            return _calc.compute(_params, _packed, _type);
        }
    }
}
