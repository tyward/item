package edu.columbia.tjw.item.fit.calculator;

import edu.columbia.tjw.item.*;
import edu.columbia.tjw.item.data.ItemFittingGrid;
import edu.columbia.tjw.item.fit.ParamFittingGrid;

public final class BaseFitCalculator<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>> implements FitCalculator<S, R, T>
{
    private final ItemFittingGrid<S, R> _grid;

    public BaseFitCalculator(final ItemFittingGrid<S, R> grid_)
    {
        _grid = grid_;
    }

    public ItemFittingGrid<S, R> getGrid()
    {
        return _grid;
    }


    public EntropyAnalysis computeEntropy(final ItemParameters<S, R, T> params_)
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
            return VACUOUS_ENTROPY;
        }

        for (int i = 0; i < grid.size(); i++)
        {
            final double entropy = model.logLikelihood(grid, i);
            final double e2 = entropy * entropy;
            entropySum += entropy;
            x2 += e2;
        }

        return new EntropyAnalysis(entropySum, x2, count);
    }


}
