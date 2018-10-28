package edu.columbia.tjw.item.fit.calculator;

import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemParameters;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.data.ItemFittingGrid;
import edu.columbia.tjw.item.util.thread.GeneralTask;
import edu.columbia.tjw.item.util.thread.GeneralThreadPool;

import java.util.ArrayList;
import java.util.List;

public class ThreadedFitCalculator<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>> implements FitCalculator<S, R, T>
{
    private static final GeneralThreadPool POOL = GeneralThreadPool.singleton();
    private final ItemFittingGrid<S, R> _grid;
    private final int _blockSize;
    private final List<FitCalculator<S, R, T>> _blockCalculators;

    public ThreadedFitCalculator(final ItemFittingGrid<S, R> grid_, final int blockSize_)
    {
        if (null == grid_)
        {
            throw new NullPointerException("Grid cannot be null.");
        }
        if (grid_.size() < 1)
        {
            throw new IllegalArgumentException("Grid must not be vacuous.");
        }

        _grid = grid_;
        _blockSize = blockSize_;

        final int numBlocks = (grid_.size() / blockSize_);

        final List<FitCalculator<S, R, T>> blockCalculators = new ArrayList<>(numBlocks);
        int start = 0;

        for (int i = 0; i < numBlocks - 1; i++)
        {
            final FittingGridShard<S, R> shard = new FittingGridShard<>(grid_, start, blockSize_);
            start += blockSize_;
            final FitCalculator<S, R, T> nextCalc = new BaseFitCalculator<>(shard);
            blockCalculators.add(nextCalc);
        }

        //Now add the last block, which may be larger than normal.
        final int lastSize = _grid.size() - start;
        final FittingGridShard<S, R> shard = new FittingGridShard<>(grid_, start, lastSize);
        final FitCalculator<S, R, T> nextCalc = new BaseFitCalculator<>(shard);
        blockCalculators.add(nextCalc);


        // Could make this synchronized or something, but probably not needed.
        _blockCalculators = blockCalculators;
    }


    @Override
    public EntropyAnalysis computeEntropy(ItemParameters<S, R, T> params_)
    {
        final List<EntropyRunner> runners = new ArrayList<>(_blockCalculators.size());

        for (final FitCalculator<S, R, T> calc : _blockCalculators)
        {
            final EntropyRunner runner = new EntropyRunner(calc, params_);
            runners.add(runner);
        }

        final List<EntropyAnalysis> analysis = POOL.runAll(runners);

        // Now process the entropy blocks.
        final EntropyAnalysis output = new EntropyAnalysis(analysis);
        return output;
    }

    private final class EntropyRunner extends GeneralTask<EntropyAnalysis>
    {
        private final FitCalculator<S, R, T> _calc;
        private final ItemParameters<S, R, T> _params;

        public EntropyRunner(final FitCalculator<S, R, T> calc_, final ItemParameters<S, R, T> params_)
        {
            _calc = calc_;
            _params = params_;
        }


        @Override
        protected EntropyAnalysis subRun() throws Exception
        {
            return _calc.computeEntropy(_params);
        }
    }


}
