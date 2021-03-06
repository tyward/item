package edu.columbia.tjw.item.fit.curve;

import edu.columbia.tjw.item.*;
import edu.columbia.tjw.item.algo.DoubleVector;
import edu.columbia.tjw.item.data.ItemFittingGrid;
import edu.columbia.tjw.item.fit.PackedParameters;
import edu.columbia.tjw.item.fit.ParamFittingGrid;
import edu.columbia.tjw.item.fit.ReducedParameterVector;
import edu.columbia.tjw.item.fit.calculator.FitPointGenerator;
import edu.columbia.tjw.item.fit.calculator.ItemFitPoint;
import edu.columbia.tjw.item.optimize.MultivariateDifferentiableFunction;
import edu.columbia.tjw.item.optimize.ThreadedMultivariateFunction;

public final class PackedCurveFunction<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
        extends ThreadedMultivariateFunction implements MultivariateDifferentiableFunction
{
    private final FitPointGenerator<S, R, T> _generator;
    private final ItemParameters<S, R, T> _unchangedParams;
    private final ItemParameters<S, R, T> _initParams;
    private final PackedParameters<S, R, T> _packed;
    private final ParamFittingGrid<S, R, T> _grid;
    private final double _origIntercept;

    private ItemModel<S, R, T> _updatedModel;

    public PackedCurveFunction(final ItemSettings settings_, final ItemCurveParams<R, T> curveParams_,
                               final S toStatus_, final ItemParameters<S, R, T> initParams_,
                               final ItemFittingGrid<S, R> grid_)
    {
        super(settings_.getThreadBlockSize(), settings_.getUseThreading());

        _generator = new FitPointGenerator<S, R, T>(grid_);

        //N.B: We need to rebuild the curve params so that we don't end up with ItemParams where a curve being
        // calibrated is
        // shared with a pre-existing item because they point to the same physical instance.
        final double[] curveVector = new double[curveParams_.size()];
        curveParams_.extractPoint(curveVector);
        final ItemCurveParams<R, T> repacked = new ItemCurveParams<>(curveParams_,
                curveParams_.getCurve(0).getCurveType().getFactory(), curveVector);

        _unchangedParams = initParams_;
        _initParams = initParams_.addBeta(repacked, toStatus_);
        _updatedModel = new ItemModel<>(_initParams);
        _grid = new ParamFittingGrid<>(_initParams, grid_);

        final int entryIndex = _initParams.getEntryCount() - 1;//_initParams.getEntryIndex(curveParams_);
        final int interceptIndex = _initParams.getInterceptIndex();
        final int toTransition = _initParams.getToIndex(toStatus_);

        final PackedParameters<S, R, T> rawPacked = _initParams.generatePacked();

        final boolean[] keep = new boolean[rawPacked.size()];

        _origIntercept = initParams_.getBeta(toTransition, _initParams.getInterceptIndex());

        for (int i = 0; i < rawPacked.size(); i++)
        {
            final int currentEntry = rawPacked.getEntry(i);

            if (rawPacked.betaIsFrozen(i))
            {
                continue;
            }


            if (currentEntry == interceptIndex && toTransition == rawPacked.getTransition(i))
            {
                keep[i] = true;
                continue;
            }

            if (currentEntry != entryIndex)
            {
                continue;
            }

            keep[i] = true;
        }

        _packed = new ReducedParameterVector<>(keep, rawPacked);

        if (_packed.size() != curveParams_.size())
        {
            throw new IllegalStateException("Impossible.");
        }
    }

    public ItemFitPoint<S, R, T> evaluate(final DoubleVector input_)
    {
        prepare(input_);
        return _generator.generatePoint(_packed);
    }

    public ItemFitPoint<S, R, T> evaluateGradient(final DoubleVector input_)
    {
        prepare(input_);
        return _generator.generateGradient(_packed);
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
    protected void prepare(DoubleVector input_)
    {
        final int dimension = this.dimension();
        boolean changed = false;

        for (int i = 0; i < dimension; i++)
        {
            double value = input_.getEntry(i);

            if (i == 0)
            {
                // Intercept term
                value += _origIntercept;
            }

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

    public ItemParameters<S, R, T> getParams()
    {
        return _updatedModel.getParams();
    }

    public ItemParameters<S, R, T> getInitParams()
    {
        return _unchangedParams;
    }


    public double calcValue(final int index_)
    {
        final ItemModel<S, R, T> localModel = _updatedModel.clone();
        return localModel.logLikelihood(_grid, index_);
    }

}
