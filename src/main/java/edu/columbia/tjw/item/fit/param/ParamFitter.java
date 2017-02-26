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

import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemModel;
import edu.columbia.tjw.item.ParamFilter;
import edu.columbia.tjw.item.ItemParameters;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemSettings;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.fit.ParamFittingGrid;
import edu.columbia.tjw.item.optimize.ConvergenceException;
import edu.columbia.tjw.item.optimize.EvaluationResult;
import edu.columbia.tjw.item.optimize.MultivariateOptimizer;
import edu.columbia.tjw.item.optimize.MultivariatePoint;
import edu.columbia.tjw.item.optimize.OptimizationResult;
import edu.columbia.tjw.item.util.LogUtil;
import java.util.Arrays;
import java.util.Collection;
import java.util.logging.Logger;

/**
 *
 * @author tyler
 * @param <S> The status type for this fitter
 * @param <R> The regressor type for this fitter
 * @param <T> The curve type for this fitter
 */
public final class ParamFitter<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
{
    private static final Logger LOG = LogUtil.getLogger(ParamFitter.class);
    private final ItemModel<S, R, T> _model;
    private final MultivariateOptimizer _optimizer;
    private final ItemSettings _settings;
    private final ParamFittingGrid<S, R, T> _grid;
    private final Collection<ParamFilter<S, R, T>> _filters;
    private final LogisticModelFunction<S, R, T> _function;

    public ParamFitter(final ItemParameters<S, R, T> params_, final ParamFittingGrid<S, R, T> grid_, final ItemSettings settings_, final Collection<ParamFilter<S, R, T>> filters_)
    {
        _model = new ItemModel<>(params_);
        _grid = grid_;
        _filters = filters_;
        _optimizer = new MultivariateOptimizer(settings_.getBlockSize(), 300, 20, 0.1);
        _settings = settings_;

        _function = generateFunction();
    }

    public double computeLogLikelihood(final ItemParameters<S, R, T> params_)
    {
        final double[] startingPoint = _function.getBeta();

        final EvaluationResult res = _function.generateResult();
        final MultivariatePoint point = new MultivariatePoint(startingPoint);
        _function.value(point, 0, _function.numRows(), res);

        final double logLikelihood = res.getMean();
        return logLikelihood;
    }

    public ItemModel<S, R, T> fit() throws ConvergenceException
    {
        //LOG.info("Fitting Coefficients");
        final double[] beta = _function.getBeta();

        final MultivariatePoint point = new MultivariatePoint(beta);

        final EvaluationResult res = _function.generateResult();
        _function.value(point, 0, _function.numRows(), res);

        final double oldLL = res.getMean();
        //LOG.info("\n\n -->Log Likelihood: " + oldLL);

        final OptimizationResult<MultivariatePoint> result = _optimizer.optimize(_function, point);

        final MultivariatePoint optimumPoint = result.getOptimum();

        for (int i = 0; i < beta.length; i++)
        {
            beta[i] = optimumPoint.getElement(i);
        }

        final double newLL = result.minValue();
        LOG.info("Fitting coefficients, LL improvement: " + oldLL + " -> " + newLL + "(" + (newLL - oldLL) + ")");

        if (!result.converged())
        {
            LOG.info("Exhausted dataset before convergence, moving on.");
        }

        if (newLL > oldLL)
        {
            return null;
        }

        final ItemParameters<S, R, T> updated = _function.generateParams(beta);
        final ItemModel<S, R, T> output = _model.updateParameters(updated);

        //LOG.info("Updated Coefficients: " + output.getParams());
        return output;
    }

    private LogisticModelFunction<S, R, T> generateFunction()
    {
        final ItemParameters<S, R, T> params = _model.getParams();
        final int reachableCount = params.getStatus().getReachableCount();
        final int entryCount = params.getEntryCount();

        final S from = params.getStatus();

        final int maxSize = reachableCount * entryCount;

        int pointer = 0;
        double[] beta = new double[maxSize];
        int[] statusPointers = new int[maxSize];
        int[] regPointers = new int[maxSize];

        for (int i = 0; i < reachableCount; i++)
        {
            final S to = from.getReachable().get(i);

            for (int k = 0; k < entryCount; k++)
            {
                if (params.betaIsFrozen(to, k, _filters))
                {
                    continue;
                }

                beta[pointer] = params.getBeta(i, k);
                statusPointers[pointer] = i;
                regPointers[pointer] = k;
                pointer++;
            }
        }

        beta = Arrays.copyOf(beta, pointer);
        statusPointers = Arrays.copyOf(statusPointers, pointer);
        regPointers = Arrays.copyOf(regPointers, pointer);

        final LogisticModelFunction<S, R, T> function = new LogisticModelFunction<>(beta, statusPointers, regPointers, params, _grid, new ItemModel<>(params), _settings);

        return function;
    }

}
