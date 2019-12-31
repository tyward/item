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
import edu.columbia.tjw.item.fit.base.BaseFitter;
import edu.columbia.tjw.item.optimize.ConvergenceException;
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
    private final ItemModel<S, R, T> _model;

    private final S _fromStatus;
    private final EntropyCalculator<S, R, T> _calc;
    private final FitResult<S, R, T> _prevResult;

    private final BaseFitter<S, R, T> _base;


    public CurveParamsFitter(final ItemCurveFactory<R, T> factory_,
                             final ItemFittingGrid<S, R> grid_, final ItemSettings settings_,
                             final FittingProgressChain<S, R, T> chain_)
    {
        final ItemParameters<S, R, T> params = chain_.getBestParameters();
        _settings = settings_;
        _factory = factory_;
        _model = new ItemModel<>(params);
        _grid = grid_;
        _fromStatus = params.getStatus();
        _prevResult = chain_.getLatestResults();
        _calc = chain_.getCalculator();
        _base = new BaseFitter<>(_calc, settings_);
    }

    public CurveFitResult<S, R, T> doCalibration(final ItemCurveParams<R, T> curveParams_,
                                                 final ItemParameters<S, R, T> reduced_, final FitResult<S, R, T> prev_,
                                                 final S toStatus_)
    {
        //First, expand the parameters.
        final ItemParameters<S, R, T> expanded = reduced_.addBeta(curveParams_,
                toStatus_);

        final FitResult<S, R, T> result = _base.doFit(expanded.generatePacked(), prev_);
        final CurveFitResult<S, R, T> output = new CurveFitResult<>(result, curveParams_, toStatus_,
                _calc.size());

        return output;
    }


    public CurveFitResult<S, R, T> calibrateExistingCurve(final int entryIndex_, final S toStatus_)
            throws ConvergenceException
    {
        final ItemParameters<S, R, T> params = _model.getParams();

        //N.B: Don't check for filtering, this is an entry we already have, filtering isn't relevant.
        final ItemCurveParams<R, T> entryParams = params.getEntryCurveParams(entryIndex_);
        final ItemParameters<S, R, T> reduced = params.dropIndex(entryIndex_);

        final CurveFitResult<S, R, T> result = expandParameters(reduced, entryParams, toStatus_, _prevResult);
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

        final CurveFitResult<S, R, T> result = doCalibration(starting, _model.getParams(), _prevResult, toStatus_);

        if (_settings.getPolishStartingParams())
        {
            try
            {
                final ItemCurveParams<R, T> polished = RawCurveCalibrator.polishCurveParameters(_factory, _settings,
                        dist, field_, starting);

                if (polished != starting)
                {
                    final CurveFitResult<S, R, T> output2 = doCalibration(polished, _model.getParams(), _prevResult,
                            toStatus_);

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
                                                    final FitResult<S, R, T> fitResult_)
            throws ConvergenceException
    {
        final CurveFitResult<S, R, T> cfr = doCalibration(initParams_, params_, _prevResult, toStatus_);
        return cfr;
    }


    public ItemParameters<S, R, T> getParams()
    {
        return _model.getParams();
    }


}
