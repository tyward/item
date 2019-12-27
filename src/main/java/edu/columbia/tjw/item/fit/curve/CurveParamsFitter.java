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

import edu.columbia.tjw.item.*;
import edu.columbia.tjw.item.algo.QuantileStatistics;
import edu.columbia.tjw.item.data.ItemFittingGrid;
import edu.columbia.tjw.item.fit.EntropyCalculator;
import edu.columbia.tjw.item.fit.FitResult;
import edu.columbia.tjw.item.fit.FittingProgressChain;
import edu.columbia.tjw.item.fit.ParamFittingGrid;
import edu.columbia.tjw.item.optimize.ConvergenceException;
import edu.columbia.tjw.item.optimize.MultivariateOptimizer;
import edu.columbia.tjw.item.optimize.MultivariatePoint;
import edu.columbia.tjw.item.optimize.OptimizationResult;
import edu.columbia.tjw.item.util.LogUtil;

import java.util.logging.Logger;

/**
 * @param <S>
 * @param <R>
 * @param <T>
 * @author tyler
 */
public final class CurveParamsFitter<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
{
    private static final Logger LOG = LogUtil.getLogger(CurveParamsFitter.class);

    private final ItemCurveFactory<R, T> _factory;
    private final ItemSettings _settings;

    private final ItemFittingGrid<S, R> _grid;

    // Getting pretty messy, we can now reset our grids and models, recache the power scores, etc...
    private final ItemModel<S, R, T> _model;

    //N.B: These are ordinals, not offsets.
    private final int[] _actualOutcomes;
    private final MultivariateOptimizer _optimizer;
    private final S _fromStatus;

    private final double _startingLL;
    private final EntropyCalculator<S, R, T> _calc;


    public CurveParamsFitter(final ItemCurveFactory<R, T> factory_,
                             final ItemFittingGrid<S, R> grid_, final ItemSettings settings_,
                             final FittingProgressChain<S, R, T> chain_)
    {
        final ItemParameters<S, R, T> params = chain_.getBestParameters();
        _settings = settings_;
        _factory = factory_;
        _model = new ItemModel<>(params);
        _grid = grid_;
        _optimizer = new MultivariateOptimizer(settings_.getBlockSize(), 300, 20, 0.1);
        _fromStatus = params.getStatus();

        final int reachableCount = _fromStatus.getReachableCount();

        final int count = _grid.size();
        _actualOutcomes = new int[count];

        for (int i = 0; i < count; i++)
        {
            _actualOutcomes[i] = _grid.getNextStatus(i);
        }

        _startingLL = chain_.getLogLikelihood();
        _calc = chain_.getCalculator();
    }

    public double getEntropy()
    {
        return _startingLL;
    }

    public CurveFitResult<S, R, T> calibrateExistingCurve(final int entryIndex_, final S toStatus_,
                                                          final double startingLL_) throws ConvergenceException
    {
        final ItemParameters<S, R, T> params = _model.getParams();

        //N.B: Don't check for filtering, this is an entry we already have, filtering isn't relevant.
        final ItemCurveParams<R, T> entryParams = params.getEntryCurveParams(entryIndex_);
        final ItemParameters<S, R, T> reduced = params.dropIndex(entryIndex_);

        final CurveFitResult<S, R, T> result = expandParameters(reduced, entryParams, toStatus_, true, startingLL_);
        final double aicDiff = result.calculateAicDifference();

        if (aicDiff > _settings.getAicCutoff())
        {
            //We demand that the AIC improvement is more than the bare minimum. 
            // Also, demand that the resulting diff is statistically significant.
            //LOG.info("AIC improvement is not large enough, keeping old curve.");
            return null;
        }

        return result;
    }

    public CurveFitResult<S, R, T> calibrateCurveAddition(T curveType_, R field_, S toStatus_)
            throws ConvergenceException
    {
        LOG.info("\nCalculating Curve[" + curveType_ + ", " + field_ + ", " + toStatus_ + "]");

        final QuantileStatistics dist = generateDistribution(field_, toStatus_);
        final ItemCurveParams<R, T> starting = _factory.generateStartingParameters(curveType_, field_, dist,
                _settings.getRandom());

        final CurveOptimizerFunction<S, R, T> func = generateFunction(starting, toStatus_, false, _model.getParams());
        final CurveFitResult<S, R, T> result = generateFit(toStatus_, _model.getParams(), func, _startingLL, starting);

        if (_settings.getPolishStartingParams())
        {
            try
            {
                final ItemCurveParams<R, T> polished = RawCurveCalibrator.polishCurveParameters(_factory, _settings,
                        dist, field_, starting);

                if (polished != starting)
                {
                    //LOG.info("Have polished parameters, testing.");

                    final CurveFitResult<S, R, T> output2 = generateFit(toStatus_, _model.getParams(), func,
                            _startingLL, polished);

                    //LOG.info("Fit comparison: " + output.getLogLikelihood() + " <> " + output2.getLogLikelihood());
                    final double aic1 = result.calculateAicDifference();
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

        return result;
    }

    private QuantileStatistics generateDistribution(R field_, S toStatus_)
    {
        final ParamFittingGrid<S, R, T> paramGrid = new ParamFittingGrid<>(_model.getParams(), _grid);
        final ItemQuantileDistribution<S, R, T> quantGenerator = new ItemQuantileDistribution<>(paramGrid,
                _model,
                _fromStatus, field_, toStatus_);
        final QuantileStatistics dist = quantGenerator.getAdjusted();
        return dist;
    }

    public CurveFitResult<S, R, T> expandParameters(final ItemParameters<S, R, T> params_,
                                                    final ItemCurveParams<R, T> initParams_, S toStatus_,
                                                    final boolean subtractStarting_, final double startingLL_)
            throws ConvergenceException
    {
        final CurveOptimizerFunction<S, R, T> func = generateFunction(initParams_, toStatus_, subtractStarting_,
                params_);
        final CurveFitResult<S, R, T> result = generateFit(toStatus_, params_, func, startingLL_, initParams_);
        return result;
    }

    private CurveOptimizerFunction<S, R, T> generateFunction(final ItemCurveParams<R, T> initParams_, S toStatus_,
                                                             final boolean subtractStarting_, final ItemParameters<S,
            R, T> params_)
    {
        final CurveOptimizerFunction<S, R, T> func = new CurveOptimizerFunction<>(initParams_, _factory, toStatus_,
                this, _actualOutcomes,
                _grid, _settings, params_);

        return func;
    }

    public CurveFitResult<S, R, T> generateFit(final S toStatus_, final ItemParameters<S, R, T> baseParams_,
                                               final CurveOptimizerFunction<S, R, T> func_,
                                               final double startingLL_, final ItemCurveParams<R, T> starting_)
            throws ConvergenceException
    {
        final double[] startingArray = starting_.generatePoint();
        final MultivariatePoint startingPoint = new MultivariatePoint(startingArray);
        final OptimizationResult<MultivariatePoint> result = _optimizer.optimize(func_, startingPoint);

        //Convert the results into params.
        final MultivariatePoint best = result.getOptimum();
        final double[] bestVal = best.getElements();
        final ItemCurveParams<R, T> curveParams = new ItemCurveParams<>(starting_, _factory, bestVal);
        final ItemParameters<S, R, T> updated = baseParams_.addBeta(curveParams, toStatus_);

        final FitResult<S, R, T> prev = new FitResult<>(baseParams_, startingLL_, result.dataElementCount());
        final FitResult<S, R, T> fitResult = _calc.computeFitResult(updated, prev);

        //N.B: The optimizer will only run until it is sure that it has found the 
        //best point, or that it can't make further progress. Its goal is not to 
        //compute the log likelihood accurately, so recompute that now. 
//        final double recalcEntropy = _calc.computeEntropy(updated).getEntropyMean();
//        final CurveFitResult<S, R, T> output = new CurveFitResult<>(baseParams_, updated, curveParams, toStatus_,
//                recalcEntropy, startingLL_, result.dataElementCount());
        final CurveFitResult<S, R, T> output = new CurveFitResult<>(fitResult, curveParams, toStatus_,
                result.dataElementCount());


        return output;
    }

    public ItemParameters<S, R, T> getParams()
    {
        return _model.getParams();
    }


}
