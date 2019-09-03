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
import edu.columbia.tjw.item.algo.QuantileApproximation;
import edu.columbia.tjw.item.algo.QuantileStatistics;
import edu.columbia.tjw.item.fit.ParamFittingGrid;
import edu.columbia.tjw.item.util.LogLikelihood;

import java.util.List;

/**
 * @param <S> The status type for this quantile distribution
 * @param <R> The regressor type for this quantile distribution
 * @author tyler
 */
public final class ItemQuantileDistribution<S extends ItemStatus<S>, R extends ItemRegressor<R>,
        T extends ItemCurveType<T>>
{
    private final LogLikelihood<S> _likelihood;
    private final QuantileStatistics _orig;
    private final QuantileStatistics _adjusted;

    public ItemQuantileDistribution(final ParamFittingGrid<S, R, T> grid_,
                                    final ItemModel<S, R, T> model_,
                                    final S fromStatus_, R field_, S toStatus_)
    {
        this(grid_, model_, fromStatus_, grid_.getRegressorReader(field_), toStatus_);
    }

    public ItemQuantileDistribution(final ParamFittingGrid<S, R, T> grid_,
                                    final ItemModel<S, R, T> model_,
                                    final S fromStatus_, final ItemRegressorReader reader_, S toStatus_)
    {
        _likelihood = new LogLikelihood<>(fromStatus_);

        final ItemRegressorReader yReader = new InnerResponseReader<>(toStatus_, grid_, model_,
                _likelihood);

        final QuantileStatistics stats = QuantileStatistics.generate(reader_, yReader);
        final QuantileApproximation approx = stats.getQuantApprox();

        final int size = approx.getSize();

        double[] adjY = new double[size];
        double[] devAdjY = new double[size];

        for (int i = 0; i < size; i++)
        {
            //We want next / exp(adjustment) = 1.0
            final double next = stats.getMeanY(i);
            final double nextDev = stats.getMeanDevY(i);
            final long nextCount = stats.getCount(i);

            if (nextCount < 1)
            {
                continue;
            }

            //We are actually looking at something like E[actual / predicted]
            final double nextMin = 0.5 / nextCount; //We can't justify a probability smaller than this given our
            // observation count.
            final double nextMax = 1.0 / nextMin; //1.0 - nextMin;
            final double boundedNext = Math.max(nextMin, Math.min(nextMax, next));

            final double adjustment = Math.log(boundedNext);

            //The operating theory here is that dev is small relative to the adjustment, so we can approximate this...
            final double adjDev = Math.log(nextDev + boundedNext) - adjustment;

            adjY[i] = adjustment;
            devAdjY[i] = adjDev;
        }

        final QuantileStatistics adjStats = new QuantileStatistics(stats, adjY, devAdjY);
        _orig = stats;
        _adjusted = adjStats;
    }


    public QuantileStatistics getAdjusted()
    {
        return _adjusted;
    }


    private static final class InnerResponseReader<S extends ItemStatus<S>, R extends ItemRegressor<R>,
            T extends ItemCurveType<T>> implements ItemRegressorReader
    {
        private final double[] _probabilities;
        private final ItemModel<S, R, T> _model;


        private final int[] _toStatusOrdinals;
        private final ParamFittingGrid<S, R, T> _grid;
        private final LogLikelihood<S> _likelihood;

        public InnerResponseReader(final S toStatus_, final ParamFittingGrid<S, R, T> grid_,
                                   final ItemModel<S, R, T> model_, final LogLikelihood<S> likelihood_)
        {
            _grid = grid_;
            _likelihood = likelihood_;
            _model = model_;
            _probabilities = new double[_model.getStatus().getReachableCount()];

            final List<S> indi = toStatus_.getIndistinguishable();
            _toStatusOrdinals = new int[indi.size()];

            for (int i = 0; i < indi.size(); i++)
            {
                _toStatusOrdinals[i] = indi.get(i).ordinal();
            }
        }

        @Override
        public double asDouble(int index_)
        {
            _model.transitionProbability(_grid, index_, _probabilities);
            final int statusIndex = _grid.getNextStatus(index_);

            double probSum = 0.0;
            double actValue = 0.0;

            for (int i = 0; i < _toStatusOrdinals.length; i++)
            {
                final int nextOffset = _likelihood.ordinalToOffset(_toStatusOrdinals[i]);
                probSum += _probabilities[nextOffset];

                if (_toStatusOrdinals[i] == statusIndex)
                {
                    //We took this transition (or one indistinguishable from it). 
                    actValue = 1.0;

                    //Don't break out, we need to sum up all the probability mass...
                    //break;
                }
            }

            //N.B: We know that we will never divide by zero here. 
            //Ideally, this ratio is approximately 1, or at least E[ratio] = 1. 
            //We can compute -ln(1/E[ratio]) and that will give us a power score adjustment we can
            //use to improve our fit. Notice that we are ignoring the non-multiplcative nature of the logistic
            // function.
            //We will need to run the optimizer over this thing eventually, but this should give us a good starting
            // point.
            final double ratio = (actValue / probSum);
            return ratio;
        }

        @Override
        public int size()
        {
            return _grid.size();
        }

    }

}
