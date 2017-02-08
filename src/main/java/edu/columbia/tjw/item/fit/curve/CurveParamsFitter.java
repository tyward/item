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
package edu.columbia.tjw.item.fit.curve;

import edu.columbia.tjw.item.ItemCurveFactory;
import edu.columbia.tjw.item.ItemCurveParams;
import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemModel;
import edu.columbia.tjw.item.ItemParameters;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemSettings;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.algo.QuantileDistribution;
import edu.columbia.tjw.item.data.ItemStatusGrid;
import edu.columbia.tjw.item.fit.ParamFittingGrid;
import edu.columbia.tjw.item.optimize.ConvergenceException;
import edu.columbia.tjw.item.optimize.MultivariateOptimizer;
import edu.columbia.tjw.item.optimize.MultivariatePoint;
import edu.columbia.tjw.item.optimize.OptimizationResult;
import edu.columbia.tjw.item.util.LogUtil;
import edu.columbia.tjw.item.util.MultiLogistic;
import edu.columbia.tjw.item.util.RectangularDoubleArray;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 *
 * @author tyler
 * @param <S>
 * @param <R>
 * @param <T>
 */
public final class CurveParamsFitter<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
{
    //private static final int BLOCK_SIZE = 200 * 1000;
    private static final Logger LOG = LogUtil.getLogger(CurveParamsFitter.class);

    private final ItemCurveFactory<R, T> _factory;

    private final ItemSettings _settings;

    private final ItemStatusGrid<S, R> _grid;
    private final RectangularDoubleArray _powerScores;

    // Getting pretty messy, we can now reset our grids and models, recache the power scores, etc...
    private final ParamFittingGrid<S, R, T> _paramGrid;
    private final ItemModel<S, R, T> _model;

    //N.B: These are ordinals, not offsets.
    private final int[] _actualOutcomes;
    private final MultivariateOptimizer _optimizer;
    private final int[] _indexList;
    private final S _fromStatus;

    public CurveParamsFitter(final ItemCurveFactory<R, T> factory_, final ItemModel<S, R, T> model_, final ItemStatusGrid<S, R> grid_, final ItemSettings settings_)
    {
        synchronized (this)
        {
            _settings = settings_;
            _factory = factory_;
            _model = model_;
            _grid = grid_;
            _optimizer = new MultivariateOptimizer(settings_.getBlockSize(), 300, 20, 0.1);

            int count = 0;

            final int gridSize = _grid.size();
            _fromStatus = model_.getParams().getStatus();
            final int fromStatusOrdinal = _fromStatus.ordinal();
            final int[] indexList = new int[gridSize];

            for (int i = 0; i < gridSize; i++)
            {
                final int statOrdinal = _grid.getStatus(i);

                if (statOrdinal != fromStatusOrdinal)
                {
                    continue;
                }
                if (!grid_.hasNextStatus(i))
                {
                    continue;
                }

                indexList[count++] = i;
            }

            final int reachableCount = _fromStatus.getReachableCount();

            _indexList = Arrays.copyOf(indexList, count);
            _powerScores = new RectangularDoubleArray(count, reachableCount);
            _actualOutcomes = new int[count];

            for (int i = 0; i < count; i++)
            {
                final int index = _indexList[i];
                _actualOutcomes[i] = _grid.getNextStatus(index);
            }

            final double[] probabilities = new double[reachableCount];
            final int baseCase = _fromStatus.getReachable().indexOf(_fromStatus);

            _paramGrid = new ParamFittingGrid<>(model_.getParams(), grid_);

            for (int i = 0; i < count; i++)
            {
                final int index = _indexList[i];

                _model.transitionProbability(_paramGrid, index, probabilities);

                MultiLogistic.multiLogitFunction(baseCase, probabilities, probabilities);

                for (int w = 0; w < reachableCount; w++)
                {
                    final double next = probabilities[w];
                    _powerScores.set(i, w, next);
                }
            }
        }
    }

    public synchronized RectangularDoubleArray getPowerScores()
    {
        return _powerScores;
    }

    public QuantileDistribution generateDistribution(R field_, S toStatus_)
    {
        final ItemQuantileDistribution<S, R> quantGenerator = new ItemQuantileDistribution<>(_paramGrid, _powerScores, _fromStatus, field_, toStatus_, _indexList);
        final QuantileDistribution dist = quantGenerator.getAdjusted();
        return dist;
    }

    public FitResult<S, R, T> expandParameters(final ItemParameters<S, R, T> params_, final ItemCurveParams<R, T> initParams_, S toStatus_,
            final boolean subtractStarting_, final double startingLL_) throws ConvergenceException
    {
        final CurveOptimizerFunction<S, R, T> func = generateFunction(initParams_, toStatus_, subtractStarting_);
        final FitResult<S, R, T> result = generateFit(toStatus_, params_, func, startingLL_, initParams_);
        return result;
    }

    public CurveOptimizerFunction<S, R, T> generateFunction(final ItemCurveParams<R, T> initParams_, S toStatus_, final boolean subtractStarting_)
    {
        final CurveOptimizerFunction<S, R, T> func = new CurveOptimizerFunction<>(initParams_, _factory, _fromStatus, toStatus_, this, _actualOutcomes,
                _paramGrid, _indexList, _settings, subtractStarting_);

        return func;
    }

    public FitResult<S, R, T> generateFit(final S toStatus_, final ItemParameters<S, R, T> baseParams_, final CurveOptimizerFunction<S, R, T> func_, final double startingLL_, final ItemCurveParams<R, T> starting_) throws ConvergenceException
    {
        final double[] startingArray = starting_.generatePoint();
        final MultivariatePoint startingPoint = new MultivariatePoint(startingArray);

        OptimizationResult<MultivariatePoint> result = _optimizer.optimize(func_, startingPoint);

        final MultivariatePoint best = result.getOptimum();
        final double[] bestVal = best.getElements();
        final ItemCurveParams<R, T> curveParams = new ItemCurveParams<>(starting_, _factory, bestVal);

        final double bestLL = result.minValue();

        final ItemParameters<S, R, T> updated = baseParams_.addBeta(curveParams, toStatus_);
        final FitResult<S, R, T> output = new FitResult<>(updated, curveParams, toStatus_, bestLL, startingLL_, result.dataElementCount());

//        LOG.info("LL change[0x" + Long.toHexString(System.identityHashCode(this)) + "L]: " + startingLL_ + " -> " + bestLL + ": " + (startingLL_ - bestLL) + " \n\n");
//        LOG.info("Updated entry: " + curveParams);
        return output;
    }

}
