package edu.columbia.tjw.item.fit.curve;

import edu.columbia.tjw.item.*;
import edu.columbia.tjw.item.fit.PackedParameters;
import edu.columbia.tjw.item.fit.ParamFittingGrid;
import edu.columbia.tjw.item.fit.ReducedParameterVector;
import edu.columbia.tjw.item.optimize.*;

public final class PackedCurveFunction<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
        extends ThreadedMultivariateFunction implements MultivariateDifferentiableFunction
{
    private final CurveParamsFitter<S, R, T> _curveFitter;
    private final ItemParameters<S, R, T> _initParams;
    private final PackedParameters<S, R, T> _packed;
    private final ParamFittingGrid<S, R, T> _grid;

    private ItemModel<S, R, T> _updatedModel;

    public PackedCurveFunction(final ItemSettings settings_, final ItemCurveParams<R, T> curveParams_, final S toStatus_, final ItemParameters<S, R, T> initParams_,
                               final ParamFittingGrid<S, R, T> grid_, final CurveParamsFitter<S, R, T> curveFitter_, final boolean subtractStarting_)
    {
        super(settings_.getThreadBlockSize(), settings_.getUseThreading());

        _curveFitter = curveFitter_;
        _initParams = initParams_.addBeta(curveParams_, toStatus_);
        _updatedModel = new ItemModel<>(_initParams);
        _grid = new ParamFittingGrid<>(_initParams, grid_.getUnderlying());

        final int entryIndex = _initParams.getEntryIndex(curveParams_);
        final int interceptIndex = _initParams.getInterceptIndex();
        final int toTransition = _initParams.getToIndex(toStatus_);

        final PackedParameters<S, R, T> rawPacked = _initParams.generatePacked();

        final boolean[] keep = new boolean[rawPacked.size()];

        for (int i = 0; i < rawPacked.size(); i++)
        {
            final int currentEntry = rawPacked.getEntry(i);

            if (rawPacked.betaIsFrozen(i))
            {
                continue;
            }

            if (currentEntry == interceptIndex)
            {
                keep[i] = true;
                continue;
            }

            if (currentEntry != entryIndex)
            {
                continue;
            }

            if (toTransition != rawPacked.getTransition(i))
            {
                continue;
            }

            keep[i] = true;
        }

        _packed = new ReducedParameterVector<>(keep, rawPacked);
    }


    @Override
    public int dimension()
    {
        return _packed.size();
    }

    @Override
    public int resultSize(int start_, int end_)
    {
        final int size = (end_ - start_);
        return size;
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
        boolean changed = false;

        for (int i = 0; i < dimension; i++)
        {
            final double value = input_.getElement(i);

            if (value != _packed.getParameter(i))
            {
                _packed.setParameter(i, value);
                changed = true;
            }
        }

        if (!changed)
        {
            return;
        }

        final ItemParameters<S, R, T> updated = _packed.generateParams();
        _updatedModel = new ItemModel<>(updated);
    }

    @Override
    protected void evaluate(int start_, int end_, EvaluationResult result_)
    {
        if (start_ == end_)
        {
            return;
        }

        final ItemModel<S, R, T> localModel = _updatedModel.clone();

        for (int i = start_; i < end_; i++)
        {
            final double ll = localModel.logLikelihood(_grid, i);

            result_.add(ll, result_.getHighWater(), i + 1);
        }

        result_.setHighRow(end_);
    }

    @Override
    protected MultivariateGradient evaluateDerivative(int start_, int end_, MultivariatePoint input_, EvaluationResult result_)
    {
        return null;
    }
}
