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

import edu.columbia.tjw.item.ItemCurve;
import edu.columbia.tjw.item.ItemCurveFactory;
import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemFittingGrid;
import edu.columbia.tjw.item.ItemModel;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemSettings;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.ItemWorkspace;
import edu.columbia.tjw.item.optimize.ConvergenceException;
import edu.columbia.tjw.item.optimize.EvaluationResult;
import edu.columbia.tjw.item.optimize.MultivariateOptimizer;
import edu.columbia.tjw.item.optimize.MultivariatePoint;
import edu.columbia.tjw.item.optimize.OptimizationResult;
import edu.columbia.tjw.item.util.LogUtil;
import edu.columbia.tjw.item.util.MultiLogistic;
import edu.columbia.tjw.item.util.RectangularDoubleArray;
import edu.columbia.tjw.item.util.random.RandomTool;
import java.util.List;
import java.util.logging.Logger;

/**
 *
 * @author tyler
 * @param <S>
 * @param <R>
 * @param <T>
 */
public class BaseCurveFitter<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>> extends CurveFitter<S, R, T>
{
    private static final int BLOCK_SIZE = 200 * 1000;
    private static final Logger LOG = LogUtil.getLogger(BaseCurveFitter.class);

    private final ItemCurveFactory<T> _factory;

    private final ItemSettings _settings;
    private final ItemModel<S, R, T> _model;
    private final ItemFittingGrid<S, R> _grid;
    private final RectangularDoubleArray _powerScores;
    private final RectangularDoubleArray _actualProbabilities;
    private final MultivariateOptimizer _optimizer;
    private final int[] _indexList;

    public BaseCurveFitter(final ItemCurveFactory<T> factory_, final ItemModel<S, R, T> model_, final ItemFittingGrid<S, R> grid_, final ItemSettings settings_)
    {
        super(factory_, model_);

        _settings = settings_;

        //_family = factory_.getFamily();
        _factory = factory_;
        _model = model_;
        _grid = grid_;
        _optimizer = new MultivariateOptimizer(BLOCK_SIZE, 300, 20, 0.1);

        int count = 0;

        final int gridSize = _grid.totalSize();
        final S fromStatus = model_.getParams().getStatus();
        final int fromStatusOrdinal = fromStatus.ordinal();

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

            count++;
        }

        final int reachableCount = fromStatus.getReachableCount();
        final ItemWorkspace<S> workspace = model_.generateWorkspace();
        final double[] probabilities = new double[reachableCount];

        _indexList = new int[count];
        _powerScores = new RectangularDoubleArray(count, reachableCount);
        _actualProbabilities = new RectangularDoubleArray(count, reachableCount);

        final List<S> reachable = fromStatus.getReachable();

        final int baseCase = fromStatus.getReachable().indexOf(fromStatus);
        int pointer = 0;

        for (int i = 0; i < count; i++)
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

            _indexList[pointer] = i;
            pointer++;
        }

        if (_settings.isRandomShuffle())
        {
            RandomTool.shuffle(_indexList, _settings.getRandom());
        }

        for (int i = 0; i < count; i++)
        {
            final int index = _indexList[i];

            model_.transitionProbability(_grid, workspace, index, probabilities);

            MultiLogistic.multiLogitFunction(baseCase, probabilities, probabilities);

            for (int w = 0; w < reachableCount; w++)
            {
                final double next = probabilities[w];
                _powerScores.set(i, w, next);

                final S stat = reachable.get(w);
                final int actualTrans = _grid.getNextStatus(index);

                if (actualTrans == stat.ordinal())
                {
                    _actualProbabilities.set(i, w, 1.0);
                }
                else
                {
                    _actualProbabilities.set(i, w, 0.0);
                }
            }
        }
    }

    @Override
    protected FitResult<S, R, T> findBest(T curveType_, R field_, S toStatus_) throws ConvergenceException
    {
        final BaseParamGenerator<S, R, T> generator = new BaseParamGenerator<>(_factory, curveType_, _model, toStatus_, _settings);
        //LOG.info("\n\nFinding best: " + generator_ + " " + field_ + " " + toStatus_);

        final CurveOptimizerFunction<S, R, T> func = new CurveOptimizerFunction<>(generator, field_, this._model.getParams().getStatus(), toStatus_, _powerScores, _actualProbabilities,
                _grid, _model, _indexList, _settings);

        final int dimension = generator.paramCount();

        //Take advantage of the fact that this starts out as all zeros, and that all zeros
        //means no change....
        final MultivariatePoint startingPoint = new MultivariatePoint(dimension);

        final EvaluationResult res = func.generateResult();
        func.value(startingPoint, 0, func.numRows(), res);

        final double startingLL = res.getMean();

        final double mean = func.getCentrality();
        final double stdDev = func.getStdDev();

        final double[] starting = generator.getStartingParams(mean, stdDev);

        for (int i = 0; i < dimension; i++)
        {
            startingPoint.setElement(i, starting[i]);
        }

        final OptimizationResult<MultivariatePoint> result = _optimizer.optimize(func, startingPoint);

        final MultivariatePoint best = result.getOptimum();
        final double[] bestVal = best.getElements();

        final ItemCurve<T> trans = generator.generateTransformation(bestVal);

        final double bestLL = result.minValue();

        final FitResult<S, R, T> output = new FitResult<S, R, T>(toStatus_, best, generator, field_, trans, bestLL, startingLL, result.dataElementCount());

        LOG.info("\nFound Curve: " + generator + " " + field_ + " " + toStatus_);
        LOG.info("Best point: " + best);
        LOG.info("LL change: " + startingLL + " -> " + bestLL + ": " + (startingLL - bestLL));
        LOG.info("AIC diff: " + output.calculateAicDifference());
        LOG.info("\n\n");

        return output;
    }

}