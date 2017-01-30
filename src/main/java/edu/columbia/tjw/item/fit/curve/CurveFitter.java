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
import edu.columbia.tjw.item.ItemModel;
import edu.columbia.tjw.item.ParamFilter;
import edu.columbia.tjw.item.util.EnumFamily;
import edu.columbia.tjw.item.ItemParameters;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemSettings;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.data.ItemStatusGrid;
import edu.columbia.tjw.item.optimize.ConvergenceException;
import edu.columbia.tjw.item.optimize.MultivariatePoint;
import edu.columbia.tjw.item.util.LogUtil;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 *
 * @author tyler
 * @param <S> The status type for this fitter
 * @param <R> The regressor type for this fitter
 * @param <T> The curve type for this fitter
 */
public abstract class CurveFitter<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
{
    private static final Logger LOG = LogUtil.getLogger(CurveFitter.class);

    private final EnumFamily<T> _family;
    private final ItemSettings _settings;
    private final ItemStatusGrid<S, R> _grid;

    public CurveFitter(final ItemCurveFactory<T> factory_, final ItemSettings settings_, final ItemStatusGrid<S, R> grid_)
    {
        if (null == settings_)
        {
            throw new NullPointerException("Settings cannot be null.");
        }

        _family = factory_.getFamily();
        _settings = settings_;
        _grid = grid_;
    }

    public final ItemModel<S, R, T> calibrateCurves()
    {
        LOG.info("Starting curve calibration sweep.");
        final ItemParameters<S, R, T> params = getParams();

        final int entryCount = params.getEntryCount();
        final List<S> statList = params.getStatus().getReachable();
        ItemModel<S, R, T> model = new ItemModel<>(params);

        //final List<T> item
        for (int i = 0; i < entryCount; i++)
        {
            final ItemCurve<T> curve = params.getEntryCurve(i, 0);

            if (null == curve)
            {
                continue;
            }

            for (final S status : statList)
            {
                try
                {
                    model = calibrateCurve(i, status);
                }
                catch (final ConvergenceException e)
                {
                    LOG.info("Trouble converging, moving on to next curve.");
                    LOG.info(e.getMessage());
                }
            }
        }

        LOG.info("Finished curve calibration sweep.");

        return model;
    }

    public final ItemModel<S, R, T> generateCurve(final Set<R> fields_, final Collection<ParamFilter<S, R, T>> filter_) throws ConvergenceException
    {
        final FitResult<S, R, T> best = findBest(fields_, filter_);

        if (null == best)
        {
            throw new ConvergenceException("Unable to improve model.");
        }

        final ItemCurve<?> trans = best._trans;

        LOG.info("Best transformation: " + trans);
        LOG.info("Best Field: " + best._field);
        LOG.info("LL improvement: " + best._startingLogL + " -> " + best._logL + ": " + best._llImprovement);
        LOG.info("Best to state: " + best._toState);
        LOG.info("Best point: " + best._point);

        final double aicDiff = best.calculateAicDifference();

        LOG.info("AIC diff: " + aicDiff);

        if (aicDiff > _settings.getAicCutoff())
        {
            //We demand that the AIC improvement is more than the bare minimum. 
            //We want this curve to be good enough to support at least N+5 parameters.
            LOG.info("AIC improvement is not large enough.");
            throw new ConvergenceException("No curves could be added with sufficient AIC improvement: " + aicDiff);
        }

        ItemModel<S, R, T> output = best.getModel();

        LOG.info("Updated parameters: \n" + output.getParams().toString());

        return output;

//        final CurveFilter<S, R, T> filter = new CurveFilter<>(output.getParams().getStatus(), best._toState, best._field, best._trans);
//        output = output.updateParameters(output.getParams().addFilter(filter));
//
//        LOG.info("Returning parameters from generate curve: " + output.getParams());
//
//        //Testing, with param fitter.
//        final ParamFittingGrid<S, R, T> grid = new ParamFittingGrid<>(output.getParams(), _grid);
//        final ParamFitter<S, R, T> fitter = new ParamFitter<>(output, _settings);
//
//        final ItemModel<S, R, T> m2 = fitter.fit(grid, null);
//
//        if (null == m2)
//        {
//            throw new ConvergenceException("Unable to improve parameter fit.");
//        }
//
//        final double llCurve = cleanTest(best._point, best._trans.getCurveType(), best._field, best._toState);
//        final double llParam = fitter.computeLogLikelihood(output.getParams(), grid, filter_);
//
//        final LogisticModelFunction<S, R, T> pFunction = fitter.generateFunction(output.getParams(), grid, filter_);
//        final CurveOptimizerFunction<S, R, T> cFunction = generateFunction(best._trans.getCurveType(), best._field, best._toState,
//                buildGenerator(best._trans.getCurveType(), best._toState, new ItemModel<>(getParams())), Double.NaN, null);
//
//        final double[] pStart = pFunction.getBeta();
//        EvaluationResult pRes = pFunction.generateResult();
//        final MultivariatePoint pPoint = new MultivariatePoint(pStart);
//        final int pRows = pFunction.numRows();
//
//        pFunction.value(pPoint, 0, pRows, pRes);
//        final double llParam2 = pRes.getMean();
//
//        final MultivariatePoint cStart = best._point;
//
//        EvaluationResult cRes = cFunction.generateResult();
//        final int cRows = cFunction.numRows();
//        cFunction.value(cStart, 0, cRows, cRes);
//        final double llCurve2 = cRes.getMean();
//
//        cRes = cFunction.generateResult();
//        pRes = pFunction.generateResult();
//
//        for (int i = 1; i < 1000; i++)
//        {
//            int testIndex = 1 + (i / 10);
//            pFunction.value(pPoint, testIndex - 1, testIndex, pRes);
//            cFunction.value(cStart, testIndex - 1, testIndex, cRes);
//
//            final double a = pRes.getMean();
//            final double b = cRes.getMean();
//            final double diff = (a - b);
//
//            LOG.info("Comparison: " + a + " =? " + b + " -> " + diff);
//        }
//
//        return m2;
        //return output;
    }

//    public abstract double cleanTest(final MultivariatePoint point_, final T curveType_, final R field_, final S toStatus_);
//
//    public abstract BaseParamGenerator<S, R, T> buildGenerator(T curveType_, S toStatus_, final ItemModel<S, R, T> model_);
//
//    public abstract CurveOptimizerFunction<S, R, T> generateFunction(T curveType_, R field_, S toStatus_, final BaseParamGenerator<S, R, T> generator_, final double prevBeta_, final ItemCurve<T> prevCurve_);
//    private final FitResult<S, R, T> generateInteraction()
    //    {
    //        final ItemParameters<S, R, T> params = getParams();
    //        final S fromStatus = params.getStatus();
    //        FitResult<S, R, T> bestResult = null;
    //        double bestImprovement = 0.0;
    //
    //        //Here's how this works, we take each existing flag and curve, and 
    //        // interact it with all the others
    //        final List<R> regressors = params.getRegressorList();
    //
    //        for (final S toStatus : fromStatus.getReachable())
    //        {
    //
    //            for (int i = 0; i < regressors.size(); i++)
    //            {
    //
    //            }
    //
    //        }
    //
    //    }
    //    /**
    //     * Attempt to interact the given curve with the curve at index_
    //     *
    //     * @param index_
    //     * @param curve_
    //     * @return
    //     */
    //    private final FitResult<S, R, T> generateOneInteraction(final int index_, final ItemCurve<T> curve_)
    //    {
    //
    //    }
    private FitResult<S, R, T> findBest(final Set<R> fields_, final Collection<ParamFilter<S, R, T>> filters_)
    {
        final ItemParameters<S, R, T> params = getParams();
        final S fromStatus = params.getStatus();
        FitResult<S, R, T> bestResult = null;
        double bestImprovement = 0.0;

        for (final S toStatus : fromStatus.getReachable())
        {
            fieldLoop:
            for (final R field : fields_)
            {
                if (params.isFiltered(fromStatus, toStatus, field, null, filters_))
                {
                    continue fieldLoop;
                }

                //if (_settings.getAllowInteractionCurves())
//                {
//                    //If we are allowing interaction curves, then attempt to fit one of those now..
//
//                }
                for (final T curveType : _family.getMembers())
                {
                    try
                    {
                        final FitResult<S, R, T> res = findBest(curveType, field, toStatus);

                        final double improvement = res.calculateAicDifference();

                        if (improvement < bestImprovement)
                        {
                            LOG.info("New Best: " + res + " -> " + improvement + " vs. " + bestImprovement);
                            bestImprovement = improvement;
                            bestResult = res;
                        }
                    }
                    catch (final ConvergenceException e)
                    {
                        LOG.info("Trouble converging, moving on to next curve.");
                        LOG.info(e.getMessage());
                    }
                    catch (final IllegalArgumentException e)
                    {
                        LOG.info("Argument trouble (" + field + "), moving on to next curve.");
                        LOG.info(e.getMessage());
                    }

                }
            }
        }

        return bestResult;
    }

    /**
     * Calibrate the given curve, but also update the model underlying this
     * fitter, if necessary.
     *
     * @param entryIndex_ The index of the entry to calibrate.
     * @return
     * @throws ConvergenceException
     */
    protected abstract ItemModel<S, R, T> calibrateCurve(final int entryIndex_, final S toStatus_) throws ConvergenceException;

    protected abstract ItemParameters<S, R, T> getParams();

    protected abstract FitResult<S, R, T> findBest(final T curveType_, final R field_, final S toStatus_) throws ConvergenceException;

    protected static final class FitResult<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
    {
        private final double _startingLogL;
        private final double _logL;
        private final double _llImprovement;
        private final ItemCurve<T> _trans;
        //private final ParamGenerator<S, R, T> _generator;
        private final R _field;
        private final MultivariatePoint _point;
        private final S _toState;
        private final int _rowCount;
        private final ItemParameters<S, R, T> _params;

        public FitResult(final S toState_, final MultivariatePoint point_, final ParamGenerator<S, R, T> generator_, final R field_,
                final ItemCurve<T> trans_, final double logLikelihood_, final double startingLL_, final int rowCount_)
        {

            _logL = logLikelihood_;
            _trans = trans_;
            _point = point_;
            _llImprovement = (startingLL_ - _logL);
            _field = field_;
            _startingLogL = startingLL_;
            _toState = toState_;
            _rowCount = rowCount_;

            final double[] params = new double[generator_.paramCount()];

            for (int i = 0; i < params.length; i++)
            {
                params[i] = _point.getElement(i);
            }

            _params = generator_.generatedModel(params, _field).getParams();
        }

        public ItemCurve<T> getTransformation()
        {
            return _trans;
        }

        public ItemModel<S, R, T> getModel()
        {
            return new ItemModel<>(_params);
        }

        public double getLogLikelihood()
        {
            return _logL;
        }

        public double improvementPerParameter()
        {
            return _llImprovement / getEffectiveParamCount();
        }

        public int getEffectiveParamCount()
        {
            return (1 + _trans.getCurveType().getParamCount());
        }

        public double calculateAicDifference()
        {
            final double scaledImprovement = _llImprovement * _rowCount;
            final double paramContribution = getEffectiveParamCount();
            final double aicDiff = 2.0 * (paramContribution - scaledImprovement);
            return aicDiff;
        }

        public String toString()
        {
            return "Fit result[" + _trans + ", " + _llImprovement + "]";
        }

    }

}
