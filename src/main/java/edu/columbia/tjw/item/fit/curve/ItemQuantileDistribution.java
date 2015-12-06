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

import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemRegressorReader;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.data.InterpolatedCurve;
import edu.columbia.tjw.item.fit.ParamFittingGrid;
import edu.columbia.tjw.item.util.LogLikelihood;
import edu.columbia.tjw.item.util.MultiLogistic;
import edu.columbia.tjw.item.util.QuantileStatistics;
import edu.columbia.tjw.item.util.RectangularDoubleArray;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author tyler
 * @param <S>
 * @param <R>
 */
public final class ItemQuantileDistribution<S extends ItemStatus<S>, R extends ItemRegressor<R>>
{
    private final LogLikelihood<S> _likelihood;

    private final double[] _powerScoreAdjustments;

    public ItemQuantileDistribution(final ParamFittingGrid<S, R, ?> grid_, final RectangularDoubleArray powerScores_, final S fromStatus_, R field_, S toStatus_)
    {
        _likelihood = new LogLikelihood<>(fromStatus_);

        final ItemRegressorReader reader = grid_.getRegressorReader(field_);
        final ItemRegressorReader yReader = new InnerResponseReader<>(toStatus_, grid_, powerScores_, _likelihood);

        final QuantileStatistics stats = new QuantileStatistics(reader, yReader);
        final int size = stats.size();

        final InterpolatedCurve quantileCurve = stats.getQuantileCurve();

        final double[] adjustments = new double[size];
        int pointer = 0;

        for (int i = 0; i < size; i++)
        {
            //We want next / exp(adjustment) = 1.0
            final double next = quantileCurve.getY(i);
            final double adjustment = Math.log(next);

            //If it so happens that the adjustment is way too low (this transition didn't happen in this bucket at all...)
            //Then we will fill it instead with nearby values. 
            if (adjustment < -50)
            {
                continue;
            }

            adjustments[pointer++] = adjustment;
        }

        if (pointer < adjustments.length)
        {
            _powerScoreAdjustments = Arrays.copyOf(adjustments, pointer);
        }
        else
        {
            _powerScoreAdjustments = adjustments;
        }

    }

    private static final class InnerResponseReader<S extends ItemStatus<S>, R extends ItemRegressor<R>> implements ItemRegressorReader
    {
        private final double[] _workspace;
        private final int[] _toStatusOrdinals;
        private final RectangularDoubleArray _powerScores;
        private final ParamFittingGrid<S, R, ?> _grid;
        private final LogLikelihood<S> _likelihood;

        public InnerResponseReader(final S toStatus_, final ParamFittingGrid<S, R, ?> grid_, final RectangularDoubleArray powerScores_, final LogLikelihood<S> likelihood_)
        {
            _grid = grid_;
            _powerScores = powerScores_;
            _likelihood = likelihood_;

            _workspace = new double[_powerScores.getColumns()];
            //_toStatusOrdinal = toStatus_.ordinal();

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
            for (int k = 0; k < _workspace.length; k++)
            {
                _workspace[k] = _powerScores.get(index_, k);
            }

            if (!_grid.hasNextStatus(index_))
            {
                return Double.NaN;
            }

            final int statusIndex = _grid.getNextStatus(index_);
            //final int offset = _likelihood.ordinalToOffset(statusIndex);
            MultiLogistic.multiLogisticFunction(_workspace, _workspace);

            double probSum = 0.0;
            double actValue = 0.0;

            for (int i = 0; i < _toStatusOrdinals.length; i++)
            {
                final int nextOffset = _likelihood.ordinalToOffset(_toStatusOrdinals[i]);
                probSum += _workspace[nextOffset];

                if (_toStatusOrdinals[i] == statusIndex)
                {
                    //We took this transition (or one indistinguishable from it). 
                    actValue = 1.0;
                    break;
                }
            }

            //N.B: We know that we will never divide by zero here. 
            //Ideally, this ratio is approximately 1, or at least E[ratio] = 1. 
            //We can compute -ln(1/E[ratio]) and that will give us a power score adjustment we can
            //use to improve our fit. Notice that we are ignoring the non-multiplcative nature of the logistic function. 
            //We will need to run the optimizer over this thing eventually, but this shoudl give us a good starting point. 
            final double ratio = (actValue / probSum);

            //final double residual = (actValue - probSum);
            return ratio;
        }

        @Override
        public int size()
        {
            return _grid.size();
        }

    }

}
