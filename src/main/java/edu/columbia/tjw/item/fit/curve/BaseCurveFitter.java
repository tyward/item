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
import edu.columbia.tjw.item.optimize.EvaluationResult;
import edu.columbia.tjw.item.optimize.MultivariateOptimizer;
import edu.columbia.tjw.item.optimize.MultivariatePoint;
import edu.columbia.tjw.item.util.LogUtil;
import edu.columbia.tjw.item.util.RectangularDoubleArray;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 *
 * @author tyler
 * @param <S> The status type for this fitter
 * @param <R> The regressor type for this fitter
 * @param <T> The curve type for this fitter
 */
public class BaseCurveFitter<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>> extends CurveFitter<S, R, T>
{
    //private static final int BLOCK_SIZE = 200 * 1000;
    private static final Logger LOG = LogUtil.getLogger(BaseCurveFitter.class);

    private final ItemCurveFactory<R, T> _factory;

    private final ItemSettings _settings;

    private final ItemStatusGrid<S, R> _grid;
    private final RectangularDoubleArray _powerScores;

    // Getting pretty messy, we can now reset our grids and models, recache the power scores, etc...
    private ParamFittingGrid<S, R, T> _paramGrid;
    private ItemModel<S, R, T> _model;

    //N.B: These are ordinals, not offsets.
    private final int[] _actualOutcomes;
    private final MultivariateOptimizer _optimizer;
    private final int[] _indexList;
    private final S _fromStatus;

    private CurveParamsFitter<S, R, T> _fitter;

    public BaseCurveFitter(final ItemCurveFactory<R, T> factory_, final ItemModel<S, R, T> model_, final ItemStatusGrid<S, R> grid_, final ItemSettings settings_, final R intercept_)
    {
        super(factory_, settings_, grid_);

        _fitter = new CurveParamsFitter<>(factory_, model_, grid_, settings_);

        if (null == intercept_)
        {
            throw new NullPointerException("Intercept cannot be null.");
        }

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

        //regenPowerScores();
        _fitter = new CurveParamsFitter<>(_factory, model_, _grid, _settings);
    }

    private QuantileDistribution generateDistribution(R field_, S toStatus_)
    {
        return _fitter.generateDistribution(field_, toStatus_);
//        final ItemQuantileDistribution<S, R> quantGenerator = new ItemQuantileDistribution<>(getParamGrid(), _powerScores, _fromStatus, field_, toStatus_, _indexList);
//        final QuantileDistribution dist = quantGenerator.getAdjusted();
//        return dist;
    }

    private void setModel(final ItemModel<S, R, T> model_)
    {
        _model = model_;
        _paramGrid = null;
        //regenPowerScores();

        _fitter = new CurveParamsFitter<>(_factory, model_, _grid, _settings);
    }

//    private synchronized ParamFittingGrid<S, R, T> getParamGrid()
//    {
//        if (null == _paramGrid)
//        {
//            _paramGrid = new ParamFittingGrid<>(_model.getParams(), _grid);
//        }
//
//        return _paramGrid;
//    }
//    private synchronized void regenPowerScores()
//    {
//        final int reachableCount = _fromStatus.getReachableCount();
//        final double[] probabilities = new double[reachableCount];
//
//        final int baseCase = _fromStatus.getReachable().indexOf(_fromStatus);
//        final int count = _actualOutcomes.length;
//
//        final ParamFittingGrid<S, R, T> paramGrid = getParamGrid();
//
//        for (int i = 0; i < count; i++)
//        {
//            final int index = _indexList[i];
//
//            _model.transitionProbability(paramGrid, index, probabilities);
//
//            MultiLogistic.multiLogitFunction(baseCase, probabilities, probabilities);
//
//            for (int w = 0; w < reachableCount; w++)
//            {
//                final double next = probabilities[w];
//                _powerScores.set(i, w, next);
//            }
//        }
//    }
//    public synchronized RectangularDoubleArray getPowerScores()
//    {
//        return _powerScores;
//    }
    private CurveOptimizerFunction<S, R, T> generateFunction(final ItemCurveParams<R, T> initParams_, S toStatus_, final boolean subtractStarting_)
    {
        return _fitter.generateFunction(initParams_, toStatus_, subtractStarting_);
//        final ParamFittingGrid<S, R, T> paramGrid = getParamGrid();
//        final CurveOptimizerFunction<S, R, T> func = new CurveOptimizerFunction<>(initParams_, _factory, _fromStatus, toStatus_, this, _actualOutcomes,
//                paramGrid, _indexList, _settings, subtractStarting_);
//
//        return func;
    }

    @Override
    protected synchronized ItemParameters<S, R, T> getParams()
    {
        return _model.getParams();
    }

    @Override
    protected FitResult<S, R, T> findBest(T curveType_, R field_, S toStatus_) throws ConvergenceException
    {
        LOG.info("\nCalculating Curve[" + curveType_ + ", " + field_ + ", " + toStatus_ + "]");

        final QuantileDistribution dist = generateDistribution(field_, toStatus_);
        final ItemCurveParams<R, T> starting = _factory.generateStartingParameters(curveType_, field_, dist, _settings.getRandom());

        final CurveOptimizerFunction<S, R, T> func = generateFunction(starting, toStatus_, false);

        final int dimension = starting.size();

        //Take advantage of the fact that this starts out as all zeros, and that all zeros
        //means no change....
        final MultivariatePoint startingPoint = new MultivariatePoint(dimension);
        final EvaluationResult res = func.generateResult();
        func.value(startingPoint, 0, func.numRows(), res);

        final double startingLL = res.getMean();

        final FitResult<S, R, T> output = generateFit(toStatus_, _model.getParams(), func, startingLL, starting);

        if (_settings.getPolishStartingParams())
        {
            try
            {
                final ItemCurveParams<R, T> polished = RawCurveCalibrator.polishCurveParameters(_factory, _settings, dist, field_, starting);

                if (polished != starting)
                {
                    //LOG.info("Have polished parameters, testing.");

                    final FitResult<S, R, T> output2 = generateFit(toStatus_, _model.getParams(), func, startingLL, polished);

                    //LOG.info("Fit comparison: " + output.getLogLikelihood() + " <> " + output2.getLogLikelihood());
                    final double aic1 = output.calculateAicDifference();
                    final double aic2 = output2.calculateAicDifference();
                    final String resString;

                    if (aic1 > aic2)
                    {
                        resString = "BETTER";
                    }
                    else if (aic2 > aic1)
                    {
                        resString = "WORSE";
                    }
                    else
                    {
                        resString = "SAME";
                    }

                    LOG.info("Polished params[" + aic1 + " <> " + aic2 + "]: " + resString);

                    if (aic1 > aic2)
                    {
                        //If the new results are actually better, return those instead.
                        return output2;
                    }
                }
            }
            catch (final Exception e)
            {
                LOG.info("Exception during polish: " + e.toString());
            }
        }

        return output;
    }

    private FitResult<S, R, T> generateFit(final S toStatus_, final ItemParameters<S, R, T> baseParams_, final CurveOptimizerFunction<S, R, T> func_, final double startingLL_, final ItemCurveParams<R, T> starting_) throws ConvergenceException
    {
        return _fitter.generateFit(toStatus_, baseParams_, func_, startingLL_, starting_);

//        final double[] startingArray = starting_.generatePoint();
//        final MultivariatePoint startingPoint = new MultivariatePoint(startingArray);
//
//        OptimizationResult<MultivariatePoint> result = _optimizer.optimize(func_, startingPoint);
//
//        final MultivariatePoint best = result.getOptimum();
//        final double[] bestVal = best.getElements();
//        final ItemCurveParams<R, T> curveParams = new ItemCurveParams<>(starting_, _factory, bestVal);
//
//        final double bestLL = result.minValue();
//
//        final ItemParameters<S, R, T> updated = baseParams_.addBeta(curveParams, toStatus_);
//        final FitResult<S, R, T> output = new FitResult<>(updated, curveParams, toStatus_, bestLL, startingLL_, result.dataElementCount());
//
////        LOG.info("LL change[0x" + Long.toHexString(System.identityHashCode(this)) + "L]: " + startingLL_ + " -> " + bestLL + ": " + (startingLL_ - bestLL) + " \n\n");
////        LOG.info("Updated entry: " + curveParams);
//        return output;
    }

    @Override
    public FitResult<S, R, T> fitEntryExpansion(final ItemParameters<S, R, T> params_, final ItemCurveParams<R, T> initParams_, S toStatus_,
            final boolean subtractStarting_, final double startingLL_) throws ConvergenceException
    {
        return _fitter.expandParameters(params_, initParams_, toStatus_, subtractStarting_, startingLL_);
//        final CurveOptimizerFunction<S, R, T> func = generateFunction(initParams_, toStatus_, subtractStarting_);
//        //final double llCheck = computeLogLikelihood(params_, _grid);
//        final FitResult<S, R, T> result = generateFit(toStatus_, params_, func, startingLL_, initParams_);
//        //final double endingLL = result.getLogLikelihood();
//        //LOG.info("LL improvement: " + startingLL_ + " -> " + endingLL);
//
//        return result;
    }

    @Override
    protected ItemModel<S, R, T> calibrateCurve(final int entryIndex_, final S toStatus_) throws ConvergenceException
    {
        final ItemParameters<S, R, T> params = _model.getParams();

        //N.B: Don't check for filtering, this is an entry we already have, filtering isn't relevant.
        final ItemCurveParams<R, T> entryParams = params.getEntryCurveParams(entryIndex_);
        final ItemParameters<S, R, T> reduced = params.dropIndex(entryIndex_);
        final double startingLL = this.computeLogLikelihood(params, _grid);

        FitResult<S, R, T> result = fitEntryExpansion(reduced, entryParams, toStatus_, true, startingLL);
        final double aicDiff = result.calculateAicDifference();

        if (aicDiff > _settings.getAicCutoff())
        {
            //We demand that the AIC improvement is more than the bare minimum. 
            // Also, demand that the resulting diff is statistically significant.
            //LOG.info("AIC improvement is not large enough, keeping old curve.");
            return _model;
        }

        LOG.info("Improved a curve:[" + aicDiff + "]: " + result.getCurveParams());
        ItemModel<S, R, T> outputModel = result.getModel();

        this.setModel(outputModel);
        return outputModel;
    }

}
