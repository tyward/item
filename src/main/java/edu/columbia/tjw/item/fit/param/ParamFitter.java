/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.columbia.tjw.item.fit.param;

import edu.columbia.tjw.item.fit.param.LogisticModelFunction;
import edu.columbia.tjw.item.ItemCurve;
import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemFittingGrid;
import edu.columbia.tjw.item.ItemModel;
import edu.columbia.tjw.item.ParamFilter;
import edu.columbia.tjw.item.ItemParameters;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.optimize.ConvergenceException;
import edu.columbia.tjw.item.optimize.EvaluationResult;
import edu.columbia.tjw.item.optimize.MultivariateDifferentiableFunction;
import edu.columbia.tjw.item.optimize.MultivariateOptimizer;
import edu.columbia.tjw.item.optimize.MultivariatePoint;
import edu.columbia.tjw.item.optimize.OptimizationResult;
import edu.columbia.tjw19.rigel.util.LogUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.logging.Logger;

/**
 *
 * @author tyler
 * @param <S>
 * @param <R>
 * @param <T>
 */
public abstract class ParamFitter<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
{
    private static final int BLOCK_SIZE = 500 * 1000;
    //private static final int BLOCK_SIZE = 10 * 1000;
    private static final Logger LOG = LogUtil.getLogger(ParamFitter.class);
    private ItemModel<S, R, T> _model;
    private final MultivariateOptimizer _optimizer;

    public ParamFitter(final ItemModel<S, R, T> model_)
    {
        _model = model_;
        _optimizer = new MultivariateOptimizer(BLOCK_SIZE, 300, 20, 0.1);
    }

    public ItemModel<S, R, T> fit(final ItemFittingGrid<S, R> grid_, final Collection<ParamFilter<S, R, T>> filters_) throws ConvergenceException
    {
        final ArrayList<ParamFilter<S, R, T>> filters = new ArrayList<>();

        if (null != filters_)
        {
            filters.addAll(filters_);
        }

        filters.addAll(_model.getParams().getFilters());

        final ItemParameters<S, R, T> params = _model.getParams();

        final int reachableCount = params.getStatus().getReachableCount();
        final int regressorCount = params.regressorCount();

        final S from = params.getStatus();

        final int maxSize = reachableCount * regressorCount;

        int pointer = 0;
        double[] beta = new double[maxSize];
        int[] statusPointers = new int[maxSize];
        int[] regPointers = new int[maxSize];

        for (int i = 0; i < reachableCount; i++)
        {
            final S to = from.getReachable().get(i);

            for (int k = 0; k < regressorCount; k++)
            {
                final R field = params.getRegressor(k);
                final ItemCurve<T> trans = params.getTransformation(k);
                boolean isFiltered = false;

                for (final ParamFilter<S, R, T> filter : filters)
                {
                    final boolean filtered = filter.isFiltered(from, to, field, trans);

                    if (filtered)
                    {
                        isFiltered = true;
                        break;
                    }
                }

                if (!isFiltered)
                {
                    beta[pointer] = params.getBeta(i, k);
                    statusPointers[pointer] = i;
                    regPointers[pointer] = k;
                    pointer++;
                }
            }
        }

        beta = Arrays.copyOf(beta, pointer);
        statusPointers = Arrays.copyOf(statusPointers, pointer);
        regPointers = Arrays.copyOf(regPointers, pointer);

        final MultivariateDifferentiableFunction function = new LogisticModelFunction<>(beta, statusPointers, regPointers, params, grid_, _model);

        final MultivariatePoint point = new MultivariatePoint(beta);

        final EvaluationResult res = function.generateResult();
        function.value(point, 0, function.numRows(), res);

//        final long start = System.currentTimeMillis();
//        final EvaluationResult tmp = function.generateResult();
//
//        for (int i = 0; i < 10; i++)
//        {
//            function.value(point, 0, function.numRows(), tmp);
//            tmp.clear();
//        }
//
//        final long end = System.currentTimeMillis();
//        final long elapsed = (end - start);
        final double oldLL = res.getMean();
        LOG.info("\n\n -->Log Likelihood: " + oldLL);

        final OptimizationResult<MultivariatePoint> result = _optimizer.optimize(function, point);

        if (!result.converged())
        {
            LOG.info("Warning: Result did not converge.");
        }

        final MultivariatePoint optimumPoint = result.getOptimum();

        for (int i = 0; i < beta.length; i++)
        {
            beta[i] = optimumPoint.getElement(i);
        }

        final double newLL = result.minValue();
        LOG.info("LL improvement: " + oldLL + " -> " + newLL);

        if (newLL > oldLL)
        {
            return null;
        }

        final ItemParameters<S, R, T> updated = updateParams(params, statusPointers, regPointers, beta);
        final ItemModel<S, R, T> output = _model.updateParameters(updated);
        return output;
    }

    public ItemModel<S, R, T> fitAndUpdate(final ItemFittingGrid<S, R> grid_, final Collection<ParamFilter<S, R, T>> filters_) throws ConvergenceException
    {
        final ItemModel<S, R, T> result = fit(grid_, filters_);

        if (null != result)
        {
            _model = result;
        }

        return result;
    }

    private ItemParameters<S, R, T> updateParams(final ItemParameters<S, R, T> params_, final int[] rowPointers_, final int[] colPointers_, final double[] betas_)
    {
        final double[][] beta = params_.getBetas();

        for (int i = 0; i < betas_.length; i++)
        {
            final int row = rowPointers_[i];
            final int column = colPointers_[i];
            final double value = betas_[i];
            beta[row][column] = value;
        }

        final ItemParameters<S, R, T> updated = params_.updateBetas(beta);
        return updated;
    }

}
