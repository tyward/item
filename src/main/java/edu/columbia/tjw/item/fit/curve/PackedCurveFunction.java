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
    private final ItemParameters<S, R, T> _unchangedParams;
    private final ItemParameters<S, R, T> _initParams;
    private final PackedParameters<S, R, T> _packed;
    private final ParamFittingGrid<S, R, T> _grid;
    private final double _origIntercept;

    private ItemModel<S, R, T> _updatedModel;

    public PackedCurveFunction(final ItemSettings settings_, final ItemCurveParams<R, T> curveParams_, final S toStatus_, final ItemParameters<S, R, T> initParams_,
                               final ParamFittingGrid<S, R, T> grid_, final CurveParamsFitter<S, R, T> curveFitter_)
    {
        super(settings_.getThreadBlockSize(), settings_.getUseThreading());

        //N.B: We need to rebuild the curve params so that we don't end up with ItemParams where a curve being calibrated is
        // shared with a pre-existing item because they point to the same physical instance.
        final double[] curveVector = new double[curveParams_.size()];
        curveParams_.extractPoint(curveVector);
        final ItemCurveParams<R, T> repacked = new ItemCurveParams<>(curveParams_, curveParams_.getCurve(0).getCurveType().getFactory(), curveVector);

        _unchangedParams = initParams_;
        _curveFitter = curveFitter_;
        _initParams = initParams_.addBeta(repacked, toStatus_);
        _updatedModel = new ItemModel<>(_initParams);
        _grid = new ParamFittingGrid<>(_initParams, grid_.getUnderlying());

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
            double value = input_.getElement(i);

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

    public double[] calcGradient(final int index_)
    {
        final ItemModel<S, R, T> localModel = _updatedModel.clone();

        final double[] gradient = new double[_packed.size()];

        localModel.computeGradient(this._grid, _packed, index_, gradient, null);
        return gradient;
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
        final double[] itemDeriv = new double[_packed.size()];
        final double[] totalDeriv = new double[_packed.size()];
        final ItemModel<S, R, T> localModel = _updatedModel.clone();

        for (int i = start_; i < end_; i++)
        {
            localModel.computeGradient(this._grid, _packed, i, itemDeriv, null);


            for (int w = 0; w < itemDeriv.length; w++)
            {
                if (Double.isNaN(itemDeriv[w]) || Double.isInfinite(itemDeriv[w]))
                {
                    System.out.println("Unexpected.");
                    localModel.computeGradient(this._grid, _packed, i, itemDeriv, null);
                }


                totalDeriv[w] += itemDeriv[w];
            }
        }

        final int count = (end_ - start_);

        //N.B: we are computing the negative log likelihood.
        final double invCount = 1.0 / count;

        for (int i = 0; i < totalDeriv.length; i++)
        {
            totalDeriv[i] = totalDeriv[i] * invCount;
        }


        final MultivariatePoint der = new MultivariatePoint(totalDeriv);

        final MultivariateGradient grad = new MultivariateGradient(input_, der, null, 0.0);
        return grad;
    }
}
