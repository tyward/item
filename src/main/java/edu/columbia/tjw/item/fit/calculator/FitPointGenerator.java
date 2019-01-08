package edu.columbia.tjw.item.fit.calculator;

import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemParameters;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.data.ItemFittingGrid;
import edu.columbia.tjw.item.fit.PackedParameters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FitPointGenerator<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
{
    public static final int DEFAULT_BLOCK_SIZE = 1000;

    private final boolean _doThreaded;
    private final ItemFittingGrid<S, R> _grid;
    private final int _blockSize;
    private final List<BlockResultCalculator<S, R, T>> _blockCalculators;

    public FitPointGenerator(final ItemFittingGrid<S, R> grid_)
    {
        this(grid_, DEFAULT_BLOCK_SIZE);
    }

    public FitPointGenerator(final ItemFittingGrid<S, R> grid_, final int blockSize_)
    {
        if (null == grid_)
        {
            throw new NullPointerException("Grid cannot be null.");
        }
        if (grid_.size() < 1)
        {
            throw new IllegalArgumentException("Grid must not be vacuous.");
        }

        // Eventually allow this as an option.
        _doThreaded = true;
        _grid = grid_;
        _blockSize = blockSize_;

        final int numBlocks = (grid_.size() / blockSize_);

        final List<BlockResultCalculator<S, R, T>> blockCalculators = new ArrayList<>(numBlocks);
        int start = 0;

        for (int i = 0; i < numBlocks - 1; i++)
        {
            final FittingGridShard<S, R> shard = new FittingGridShard<>(grid_, start, blockSize_);
            final BlockResultCalculator<S, R, T> nextCalc = new BlockResultCalculator<>(shard, start);
            start += blockSize_;
            blockCalculators.add(nextCalc);
        }

        //Now add the last block, which may be larger than normal.
        final int lastSize = _grid.size() - start;
        final FittingGridShard<S, R> shard = new FittingGridShard<>(grid_, start, lastSize);
        final BlockResultCalculator<S, R, T> nextCalc = new BlockResultCalculator<>(shard, start);
        blockCalculators.add(nextCalc);

        // Could make this synchronized or something, but probably not needed.
        _blockCalculators = Collections.unmodifiableList(blockCalculators);
    }

    public FitPoint<S, R, T> generatePoint(final ItemParameters<S, R, T> params_)
    {
        return generatePoint(params_.generatePacked());
    }

    public FitPoint<S, R, T> generatePoint(final PackedParameters<S, R, T> packed_)
    {
        return new FitPoint<>(this, packed_);
    }

    public int getBlockCount()
    {
        return _blockCalculators.size();
    }

    public int getBlockSize()
    {
        return _blockSize;
    }

    public List<BlockResultCalculator<S, R, T>> getCalculators()
    {
        return _blockCalculators;
    }
}
