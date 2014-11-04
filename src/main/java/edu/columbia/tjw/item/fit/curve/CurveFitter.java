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
import edu.columbia.tjw.item.optimize.ConvergenceException;
import edu.columbia.tjw.item.optimize.MultivariatePoint;
import edu.columbia.tjw.item.util.LogUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 *
 * @author tyler
 * @param <S>
 * @param <R>
 * @param <T>
 */
public abstract class CurveFitter<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
{
    private static final Logger LOG = LogUtil.getLogger(CurveFitter.class);
    private static final double AIC_CUTOFF = -5.0;

    private final EnumFamily<T> _family;
    private final ItemParameters<S, R, T> _params;
    private final ItemSettings _settings;

    public CurveFitter(final ItemCurveFactory<T> factory_, final ItemModel<S, R, T> model_, final ItemSettings settings_)
    {
        _family = factory_.getFamily();
        _params = model_.getParams();
        _settings = settings_;
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
        final CurveFilter<S, R, T> filter = new CurveFilter<>(output.getParams().getStatus(), best._toState, best._field, best._trans);
        output = output.updateParameters(output.getParams().addFilter(filter));
        return output;
    }

    private final FitResult<S, R, T> findBest(final Set<R> fields_, final Collection<ParamFilter<S, R, T>> filters_)
    {
        final S fromStatus = _params.getStatus();
        FitResult<S, R, T> bestResult = null;
        double bestImprovement = 0.0;

        final List<ParamFilter<S, R, T>> filters = new ArrayList<>();
        filters.addAll(_params.getFilters());

        if (null != filters_)
        {
            filters.addAll(filters_);
        }

        for (final S toStatus : fromStatus.getReachable())
        {
            fieldLoop:
            for (final R field : fields_)
            {
                for (final ParamFilter<S, R, T> filter : filters)
                {
                    if (filter.isFiltered(fromStatus, toStatus, field, null))
                    {
                        continue fieldLoop;
                    }
                }

                for (final T curveType : _family.getMembers())
                {
                    try
                    {
                        final FitResult<S, R, T> res = findBest(curveType, field, toStatus);

                        final double improvement = res.calculateAicDifference();

                        if (improvement < bestImprovement)
                        {
                            LOG.info("New Best: " + res);
                            bestImprovement = improvement;
                            bestResult = res;
                        }
                    }
                    catch (final ConvergenceException e)
                    {
                        LOG.info("Trouble converging, moving on to next curve.");
                        LOG.info(e.getMessage());
                    }
                }
            }
        }

        return bestResult;
    }

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
            //_generator = generator_;
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
    }

}
