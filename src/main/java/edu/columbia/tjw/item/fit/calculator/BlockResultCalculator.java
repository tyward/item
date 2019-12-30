package edu.columbia.tjw.item.fit.calculator;

import edu.columbia.tjw.item.*;
import edu.columbia.tjw.item.data.ItemFittingGrid;
import edu.columbia.tjw.item.fit.PackedParameters;
import edu.columbia.tjw.item.fit.ParamFittingGrid;

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


    public synchronized BlockResult compute(final ItemParameters<S, R, T> params_,
                                          final PackedParameters<S, R, T> packed_,
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
        final double[][] secondDerivative;
        final double[][] fisherInformation;

        if (type_ == BlockCalculationType.SECOND_DERIVATIVE)
        {
            final int dimension = packed_.size();
            derivative = new double[dimension];
            fisherInformation = new double[dimension][dimension];
            secondDerivative = new double[dimension][dimension];
        }
        else if (type_ == BlockCalculationType.FIRST_DERIVATIVE)
        {
            final int dimension = packed_.size();
            derivative = new double[dimension];
            fisherInformation = new double[dimension][dimension];
            secondDerivative = null;
        }
        else
        {
            derivative = null;
            fisherInformation = null;
            secondDerivative = null;
        }

        if (derivative != null)
        {
            final int dimension = derivative.length;
            final double[] tmp = new double[dimension];
            final double[][] tmp2;

            if (secondDerivative != null)
            {
                tmp2 = new double[dimension][dimension];
            }
            else
            {
                tmp2 = null;
            }

            for (int i = 0; i < count; i++)
            {
                model.computeGradient(grid, packed_, i, tmp, tmp2);

                for (int k = 0; k < dimension; k++)
                {
                    derivative[k] += tmp[k];

                    for (int w = 0; w < dimension; w++)
                    {
                        fisherInformation[k][w] += tmp[k] * tmp[w];
                    }

                    if (secondDerivative != null)
                    {
                        for (int w = 0; w < dimension; w++)
                        {
                            secondDerivative[k][w] += tmp2[k][w];
                        }
                    }
                }
            }

            if (count > 0)
            {
                //N.B: we are computing the negative log likelihood.
                final double invCount = 1.0 / count;

                for (int i = 0; i < dimension; i++)
                {
                    derivative[i] = derivative[i] * invCount;

                    for (int w = 0; w < dimension; w++)
                    {
                        fisherInformation[i][w] *= invCount;
                    }

                    if (secondDerivative != null)
                    {
                        for (int w = 0; w < dimension; w++)
                        {
                            secondDerivative[i][w] *= invCount;
                        }
                    }
                }
            }
        }

        return new BlockResult(_rowOffset, _rowOffset + count, entropySum, x2,
                derivative, fisherInformation, secondDerivative);
    }
}
