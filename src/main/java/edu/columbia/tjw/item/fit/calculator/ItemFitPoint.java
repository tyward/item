package edu.columbia.tjw.item.fit.calculator;

import edu.columbia.tjw.item.*;
import edu.columbia.tjw.item.algo.DoubleVector;
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
    private final ItemModel<S, R, T> _model;
    private final int _blockSize;
    private final int _totalSize;
    private final int _dimension;

    private final BlockResultCompound[] _compound;
    private int[] _nextBlock;

    private DoubleVector _params;

    private BlockResult _prevBlockResult = null;

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

        _dimension = packed_.size();
        _blockCalculators = calculator_.getCalculators();
        _model = new ItemModel<>(packed_);
        _blockSize = calculator_.getBlockSize();
        _totalSize = calculator_.getRowCount();

        _nextBlock = new int[BlockCalculationType.getValueCount()];
        _compound = new BlockResultCompound[BlockCalculationType.getValueCount()];

        for (int i = 0; i < _compound.length; i++)
        {
            _compound[i] = new BlockResultCompound();
        }

        _params = packed_.getPacked();
    }

    @Override
    public int getDimension()
    {
        return _dimension;
    }

    public ItemParameters<S, R, T> getParams()
    {
        return _model.getParams();
    }

    @Override
    public DoubleVector getParameters()
    {
        return _params;
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
        computeAll(type_, null);
    }

    @Override
    public void computeAll(BlockCalculationType type_, final BlockResult prevDerivative_)
    {
        computeUntil(getBlockCount(), type_, prevDerivative_);
    }

    @Override
    public BlockResult getAggregated(BlockCalculationType type_)
    {
        return _compound[type_.ordinal()].getAggregated();
    }

    @Override
    public void computeUntil(final int endBlock_, BlockCalculationType type_)
    {
        computeUntil(endBlock_, type_, null);
    }

    /**
     * Reset this to a pristine computation state.
     */
    public void clear()
    {
        for (int i = 0; i < _compound.length; i++)
        {
            _compound[i] = new BlockResultCompound();
            _nextBlock[i] = 0;
        }
    }

    @Override
    public void computeUntil(final int endBlock_, BlockCalculationType type_, final BlockResult prevDerivative_)
    {
        if (_prevBlockResult != prevDerivative_)
        {
            this.clear();
            _prevBlockResult = prevDerivative_;
        }

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
            final EntropyRunner runner = new EntropyRunner(calc, type_, prevDerivative_);
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


    public int getSize()
    {
        return this._totalSize;
    }


    private final class EntropyRunner extends GeneralTask<BlockResult>
    {
        private final BlockResultCalculator<S, R, T> _calc;
        private final BlockCalculationType _type;
        private final BlockResult _prevDerivative;

        public EntropyRunner(final BlockResultCalculator<S, R, T> calc_,
                             final BlockCalculationType type_, final BlockResult prevDerivative_)
        {
            _calc = calc_;
            _type = type_;
            _prevDerivative = prevDerivative_;
        }

        @Override
        protected BlockResult subRun() throws Exception
        {
            // N.B: we clone the model since ItemModel isn't threadsafe (it has internal state).
            // However, cloning models is a bit faster than making new ones because of the internal (immutable)
            // parameters.
            return _calc.compute(_model.clone(), _type, _prevDerivative);
        }
    }
}
