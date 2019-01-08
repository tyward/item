package edu.columbia.tjw.item.fit.calculator;

import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemParameters;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.fit.PackedParameters;
import edu.columbia.tjw.item.util.thread.GeneralTask;
import edu.columbia.tjw.item.util.thread.GeneralThreadPool;

import java.util.ArrayList;
import java.util.List;

public final class FitPoint<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
{
    private static final GeneralThreadPool POOL = GeneralThreadPool.singleton();

    private final List<BlockResultCalculator<S, R, T>> _blockCalculators;
    private final PackedParameters<S, R, T> _packed;
    private final ItemParameters<S, R, T> _params;
    private final BlockResultCompound _compound;
    private final int _blockSize;

    private int _nextBlock;

    public FitPoint(final FitPointGenerator<S, R, T> calculator_, final PackedParameters<S, R, T> packed_)
    {
        _blockCalculators = calculator_.getCalculators();
        _packed = packed_;
        _params = _packed.generateParams();
        _compound = new BlockResultCompound();
        _blockSize = calculator_.getBlockSize();
        _nextBlock = 0;
    }

    public int getBlockSize() {
        return _blockSize;
    }

    public int getBlockCount()
    {
        return _blockCalculators.size();
    }

    public int getNextBlock()
    {
        return _nextBlock;
    }

    public void computeAll()
    {
        computeUntil(getBlockCount());
    }

    public BlockResult getAggregated()
    {
        return _compound.getAggregated();
    }

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

    public BlockResult getBlock(final int index_)
    {
        return _compound.getBlock(index_);
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
            return _calc.compute(_params);
        }
    }
}
