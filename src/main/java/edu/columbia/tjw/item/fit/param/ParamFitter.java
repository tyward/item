/*
 * Copyright 2014 Tyler Ward.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * This code is part of the reference implementation of http://arxiv.org/abs/1409.6075
 * 
 * This is provided as an example to help in the understanding of the ITEM model system.
 */
package edu.columbia.tjw.item.fit.param;

import edu.columbia.tjw.item.ItemCurve;
import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.data.ItemFittingGrid;
import edu.columbia.tjw.item.ItemModel;
import edu.columbia.tjw.item.ParamFilter;
import edu.columbia.tjw.item.ItemParameters;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemSettings;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.optimize.ConvergenceException;
import edu.columbia.tjw.item.optimize.EvaluationResult;
import edu.columbia.tjw.item.optimize.MultivariateOptimizer;
import edu.columbia.tjw.item.optimize.MultivariatePoint;
import edu.columbia.tjw.item.optimize.OptimizationResult;
import edu.columbia.tjw.item.util.LogUtil;
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
public final class ParamFitter<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
{
    private static final Logger LOG = LogUtil.getLogger(ParamFitter.class);
    private ItemModel<S, R, T> _model;
    private final MultivariateOptimizer _optimizer;
    private final ItemSettings _settings;

    public ParamFitter(final ItemModel<S, R, T> model_, final ItemSettings settings_)
    {
        _model = model_;
        _optimizer = new MultivariateOptimizer(settings_.getBlockSize(), 300, 20, 0.1);
        _settings = settings_;
    }

    public LogisticModelFunction<S, R, T> generateFunction(final ItemParameters<S, R, T> params_, final ItemFittingGrid<S, R> grid_, final Collection<ParamFilter<S, R, T>> filters_)
    {
        final ArrayList<ParamFilter<S, R, T>> filters = new ArrayList<>();

        if (null != filters_)
        {
            filters.addAll(filters_);
        }

        filters.addAll(params_.getFilters());

        final int reachableCount = params_.getStatus().getReachableCount();
        final int regressorCount = params_.regressorCount();

        final S from = params_.getStatus();

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
                final R field = params_.getRegressor(k);
                final ItemCurve<T> trans = params_.getTransformation(k);
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
                    beta[pointer] = params_.getBeta(i, k);
                    statusPointers[pointer] = i;
                    regPointers[pointer] = k;
                    pointer++;
                }
            }
        }

        beta = Arrays.copyOf(beta, pointer);
        statusPointers = Arrays.copyOf(statusPointers, pointer);
        regPointers = Arrays.copyOf(regPointers, pointer);

        final LogisticModelFunction<S, R, T> function = new LogisticModelFunction<>(beta, statusPointers, regPointers, params_, grid_, new ItemModel<>(params_), _settings);

        return function;
    }

    public double computeLogLikelihood(final ItemParameters<S, R, T> params_, final ItemFittingGrid<S, R> grid_, final Collection<ParamFilter<S, R, T>> filters_)
    {
        final LogisticModelFunction<S, R, T> function = generateFunction(params_, grid_, filters_);

        final double[] startingPoint = function.getBeta();

        final EvaluationResult res = function.generateResult();
        final MultivariatePoint point = new MultivariatePoint(startingPoint);
        function.value(point, 0, function.numRows(), res);

        final double logLikelihood = res.getMean();
        return logLikelihood;
    }

    public ItemModel<S, R, T> fit(final ItemFittingGrid<S, R> grid_, final Collection<ParamFilter<S, R, T>> filters_) throws ConvergenceException
    {

        final LogisticModelFunction<S, R, T> function = generateFunction(_model.getParams(), grid_, filters_);
        final double[] beta = function.getBeta();

        final MultivariatePoint point = new MultivariatePoint(beta);

        final EvaluationResult res = function.generateResult();
        function.value(point, 0, function.numRows(), res);

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

        final ItemParameters<S, R, T> updated = function.generateParams(beta);
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
