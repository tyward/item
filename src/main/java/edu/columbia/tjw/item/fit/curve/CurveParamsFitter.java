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
import edu.columbia.tjw.item.fit.FitResult;
import edu.columbia.tjw.item.fit.ParamFittingGrid;
import edu.columbia.tjw.item.fit.base.BaseFitter;
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

    private final ItemSettings _settings;
    private final BaseFitter<S, R, T> _base;


    public CurveParamsFitter(final ItemSettings settings_,
                             final BaseFitter<S, R, T> base_)
    {
        _settings = settings_;
        _base = base_;
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
                _base.getCalc().getGrid().size());

        return output;
    }


    public CurveFitResult<S, R, T> calibrateExistingCurve(final int entryIndex_, final S toStatus_, final FitResult<S
            , R, T> prevResult_)
    {
        final ItemParameters<S, R, T> params = prevResult_.getParams();

        //N.B: Don't check for filtering, this is an entry we already have, filtering isn't relevant.
        final ItemCurveParams<R, T> entryParams = params.getEntryCurveParams(entryIndex_);
        final ItemParameters<S, R, T> reduced = params.dropIndex(entryIndex_);

        final CurveFitResult<S, R, T> result = doCalibration(entryParams, reduced, prevResult_, toStatus_);
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

    public CurveFitResult<S, R, T> calibrateCurveAddition(T curveType_, R field_, S toStatus_,
                                                          final FitResult<S, R, T> prevResult_)
    {
        LOG.info("\nCalculating Curve[" + curveType_ + ", " + field_ + ", " + toStatus_ + "]");

        final ItemParameters<S, R, T> params = prevResult_.getParams();
        final ItemCurveFactory<R, T> factory = params.getCurveFamily().getFromOrdinal(0).getFactory();

        final QuantileStatistics dist = generateDistribution(field_, toStatus_, prevResult_);
        final ItemCurveParams<R, T> starting = factory.generateStartingParameters(curveType_, field_, dist,
                _settings.getRandom());

        final CurveFitResult<S, R, T> result = doCalibration(starting, params, prevResult_, toStatus_);

        if (_settings.getPolishStartingParams())
        {
            try
            {
                final ItemCurveParams<R, T> polished = RawCurveCalibrator.polishCurveParameters(factory, _settings,
                        dist, starting);

                if (polished != starting)
                {
                    final CurveFitResult<S, R, T> output2 = doCalibration(polished, params,
                            prevResult_,
                            toStatus_);

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

    private QuantileStatistics generateDistribution(R field_, S toStatus_, FitResult<S, R, T> fitResult_)
    {
        final ItemParameters<S, R, T> params = fitResult_.getParams();
        final ParamFittingGrid<S, R, T> paramGrid = new ParamFittingGrid<>(params, _base.getCalc().getGrid());
        final ItemModel<S, R, T> model = new ItemModel<>(params);
        final ItemQuantileDistribution<S, R, T> quantGenerator = new ItemQuantileDistribution<>(paramGrid,
                model,
                params.getStatus(), field_, toStatus_);
        final QuantileStatistics dist = quantGenerator.getAdjusted();
        return dist;
    }


}
