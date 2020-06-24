package edu.columbia.tjw.item.fit.base;

import edu.columbia.tjw.item.*;
import edu.columbia.tjw.item.algo.DoubleVector;
import edu.columbia.tjw.item.data.ItemFittingGrid;
import edu.columbia.tjw.item.fit.PackedParameters;
import edu.columbia.tjw.item.fit.ParamFittingGrid;
import edu.columbia.tjw.item.fit.calculator.FitPointGenerator;
import edu.columbia.tjw.item.fit.calculator.ItemFitPoint;
import edu.columbia.tjw.item.optimize.MultivariateDifferentiableFunction;
import edu.columbia.tjw.item.optimize.MultivariatePoint;
import edu.columbia.tjw.item.optimize.ThreadedMultivariateFunction;

public class BaseModelFunction<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
        extends ThreadedMultivariateFunction implements MultivariateDifferentiableFunction
{
    private final FitPointGenerator<S, R, T> _generator;
    private final ParamFittingGrid<S, R, T> _grid;
    private final PackedParameters<S, R, T> _packed;


    public BaseModelFunction(final ItemFittingGrid<S, R> grid_, ItemSettings settings_,
                             final PackedParameters<S, R, T> packedStarting_)
    {
        super(settings_.getThreadBlockSize(), settings_.getUseThreading());

        final ParamFittingGrid<S, R, T> grid = new ParamFittingGrid<>(packedStarting_.getOriginalParams(), grid_);

        _generator = new FitPointGenerator<>(grid_);
        _grid = grid;
        _packed = packedStarting_.clone();
    }

    public ItemFitPoint<S, R, T> evaluate(final MultivariatePoint input_)
    {
        prepare(input_);
        return _generator.generatePoint(_packed);
    }

    public ItemFitPoint<S, R, T> evaluateGradient(final MultivariatePoint input_)
    {
        prepare(input_);
        return _generator.generateGradient(_packed);
    }

    public DoubleVector getBeta()
    {
        return _packed.getPacked();
    }

    public ItemParameters<S, R, T> generateParams(final double[] beta_)
    {
        _packed.updatePacked(beta_);
        final ItemParameters<S, R, T> p2 = _packed.generateParams();
        return p2;
    }

    @Override
    public int dimension()
    {
        return _packed.size();
    }

    @Override
    public int numRows()
    {
        return _grid.size();
    }

    @Override
    protected void prepare(MultivariatePoint input_)
    {
        final int dimension = this.dimension();

        for (int i = 0; i < dimension; i++)
        {
            final double value = input_.getElement(i);
            _packed.setParameter(i, value);
        }
    }

    @Override
    public int resultSize(int start_, int end_)
    {
        return (end_ - start_);
    }

}

