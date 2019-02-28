package edu.columbia.tjw.item.fit.calculator;

import edu.columbia.tjw.item.*;
import edu.columbia.tjw.item.data.ItemFittingGrid;
import edu.columbia.tjw.item.fit.PackedParameters;
import edu.columbia.tjw.item.fit.ParamFittingGrid;
import edu.columbia.tjw.item.optimize.MultivariateGradient;
import edu.columbia.tjw.item.optimize.MultivariatePoint;

import java.util.Arrays;

public final class BlockResultCalculator<S extends ItemStatus<S>, R extends ItemRegressor<R>,
        T extends ItemCurveType<T>>
{
    private final ItemFittingGrid<S, R> _grid;
    private final int _rowOffset;

    public BlockResultCalculator(final ItemFittingGrid<S, R> grid_)
    {
        this(grid_, 0);
    }

    public BlockResultCalculator(final ItemFittingGrid<S, R> grid_, final int rowOffset_)
    {
        if (null == grid_)
        {
            throw new NullPointerException("Grid cannot be null.");
        }
        if (rowOffset_ < 0)
        {
            throw new IllegalArgumentException("Row offset must be nonnegative: " + rowOffset_);
        }

        _grid = grid_;
        _rowOffset = rowOffset_;
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

        final double[] derivative;

        if(type_ == BlockCalculationType.FIRST_DERIVATIVE || type_ == BlockCalculationType.SECOND_DERIVATIVE) {
            final int dimension = packed_.size();
            final double[] tmp = new double[dimension];
            derivative = new double[dimension];

            for (int i = 0; i < count; i++)
            {
                model.computeGradient(grid, packed_, i, tmp, null);

                for (int k = 0; k < dimension; k++)
                {
                    derivative[k] += tmp[k];
                }
            }

            if (count > 0)
            {
                //N.B: we are computing the negative log likelihood.
                final double invCount = 1.0 / count;

                for (int i = 0; i < dimension; i++)
                {
                    derivative[i] = derivative[i] * invCount;
                }
            }
        } else {
            derivative = null;
        }

        return new BlockResult(_rowOffset, _rowOffset + count, entropySum, x2, derivative);
    }
}
