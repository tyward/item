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

public final class ItemFitPoint<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>> implements FitPoint
{
    private static final GeneralThreadPool POOL = GeneralThreadPool.singleton();

    private final List<BlockResultCalculator<S, R, T>> _blockCalculators;
    private final PackedParameters<S, R, T> _packed;
    private final ItemParameters<S, R, T> _params;
    private final BlockResultCompound _compound;
    private final int _blockSize;
    private final int _totalSize;
    private final BlockCalculationType _type;

    private int _nextBlock;

    public ItemFitPoint(final FitPointGenerator<S, R, T> calculator_, final PackedParameters<S, R, T> packed_,
                        BlockCalculationType type_)
    {
        if (null == calculator_)
        {
            throw new NullPointerException("Calculator cannot be null.");
        }
        if (null == packed_)
        {
            throw new NullPointerException("Packed cannot be null.");
        }
        if (null == type_)
        {
            throw new NullPointerException("Type cannot be null.");
        }

        _blockCalculators = calculator_.getCalculators();
        _packed = packed_.clone(); // May be able to avoid, for now, for safety.
        _params = _packed.generateParams();
        _compound = new BlockResultCompound();
        _blockSize = calculator_.getBlockSize();
        _totalSize = calculator_.getRowCount();
        _nextBlock = 0;
        _type = type_;
    }

    public ItemFitPoint(final FitPointGenerator<S, R, T> calculator_, final PackedParameters<S, R, T> packed_)
    {
        this(calculator_, packed_, BlockCalculationType.VALUE);
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
    public int getNextBlock()
    {
        return _nextBlock;
    }

    @Override
    public void computeAll()
    {
        computeUntil(getBlockCount());
    }

    @Override
    public BlockResult getAggregated()
    {
        return _compound.getAggregated();
    }

    @Override
    public void computeUntil(final int endBlock_)
    {
        final int neededBlocks = endBlock_ - _nextBlock;

        if (neededBlocks <= 0)
        {
            return;
        }

        final List<EntropyRunner> runners = new ArrayList<>(neededBlocks);

        for (int i = _nextBlock; i < endBlock_; i++)
        {
            final BlockResultCalculator<S, R, T> calc = _blockCalculators.get(i);
            final EntropyRunner runner = new EntropyRunner(calc, _params);
            runners.add(runner);
        }

        final List<BlockResult> analysis = POOL.runAll(runners);

        for (final BlockResult result : analysis)
        {
            _compound.appendResult(result);
        }

        _nextBlock = endBlock_;
    }

    @Override
    public BlockResult getBlock(final int index_)
    {
        return _compound.getBlock(index_);
    }

    @Override
    public double getMean(int boundary_)
    {
        if (boundary_ == 0)
        {
            return 0.0;
        }
        this.computeUntil(boundary_);

        final VarianceCalculator vcalc = new VarianceCalculator();

        for (int i = 0; i < boundary_; i++)
        {
            final double next = getBlock(i).getEntropyMean();
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
        this.computeUntil(boundary_);

        final VarianceCalculator vcalc = new VarianceCalculator();

        for (int i = 0; i < boundary_; i++)
        {
            final double next = getBlock(i).getEntropyMean();
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

        public EntropyRunner(final BlockResultCalculator<S, R, T> calc_, final ItemParameters<S, R, T> params_)
        {
            _calc = calc_;
            _params = params_;
        }


        @Override
        protected BlockResult subRun() throws Exception
        {
            return _calc.compute(_params, _packed, _type);
        }
    }
}
