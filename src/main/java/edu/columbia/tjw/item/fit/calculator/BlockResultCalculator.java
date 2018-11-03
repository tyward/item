package edu.columbia.tjw.item.fit.calculator;

import edu.columbia.tjw.item.*;
import edu.columbia.tjw.item.data.ItemFittingGrid;
import edu.columbia.tjw.item.fit.PackedParameters;
import edu.columbia.tjw.item.fit.ParamFittingGrid;

public final class BlockResultCalculator<S extends ItemStatus<S>, R extends ItemRegressor<R>,
        T extends ItemCurveType<T>>
{
    private final ItemFittingGrid<S, R> _grid;

    public BlockResultCalculator(final ItemFittingGrid<S, R> grid_)
    {
        _grid = grid_;
    }

    public ItemFittingGrid<S, R> getGrid()
    {
        return _grid;
    }

    public BlockResult compute(final ItemParameters<S, R, T> params_)
    {
        return compute(params_, null, BlockCalculationType.VALUE);
    }


    public BlockResult compute(final ItemParameters<S, R, T> params_, final PackedParameters<S, R, T> packed_,
                               final BlockCalculationType type_)
    {
        if (params_.getStatus() != _grid.getFromStatus())
        {
            throw new IllegalArgumentException("Status mismatch.");
        }

        final ParamFittingGrid<S, R, T> grid = new ParamFittingGrid<>(params_, _grid);
        final ItemModel<S, R, T> model = new ItemModel<>(params_);

        double entropySum = 0.0;
        double x2 = 0.0;
        final int count = grid.size();

        if (count <= 0)
        {
            throw new IllegalArgumentException("Grid must have positive size.");
        }

        for (int i = 0; i < grid.size(); i++)
        {
            final double entropy = model.logLikelihood(grid, i);
            final double e2 = entropy * entropy;
            entropySum += entropy;
            x2 += e2;
        }

        return new BlockResult(0, count, entropySum, x2);
    }
}
