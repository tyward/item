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
import edu.columbia.tjw.item.fit.ParamFittingGrid;
import edu.columbia.tjw.item.util.LogLikelihood;
import edu.columbia.tjw.item.util.QuantileStatistics;
import edu.columbia.tjw.item.util.RectangularDoubleArray;

/**
 *
 * @author tyler
 * @param <S>
 * @param <R>
 */
public final class ItemQuantileGenerator<S extends ItemStatus<S>, R extends ItemRegressor<R>>
{
    private final ParamFittingGrid<S, R, ?> _grid;
    private final RectangularDoubleArray _powerScores;
    private final LogLikelihood<S> _likelihood;

    public ItemQuantileGenerator(final ParamFittingGrid<S, R, ?> grid_, final RectangularDoubleArray powerScores_, final S fromStatus_)
    {

        _grid = grid_;
        _powerScores = powerScores_;
        _likelihood = new LogLikelihood<>(fromStatus_);
    }

    public QuantileStatistics generateStatistics(R field_, S toStatus_)
    {
        final ItemRegressorReader reader = _grid.getRegressorReader(field_);
        final ItemRegressorReader yReader = new InnerResponseReader<>(toStatus_);

        final QuantileStatistics stats = new QuantileStatistics(reader, yReader);

        return stats;
    }

    private final class InnerResponseReader<S extends ItemStatus<S>> implements ItemRegressorReader
    {
        //private final double[] _workspace;
        private final int _toStatusOrdinal;

        public InnerResponseReader(final S toStatus_)
        {
            //_workspace = new double[_powerScores.getColumns()];
            _toStatusOrdinal = toStatus_.ordinal();
        }

        @Override
        public double asDouble(int index_)
        {
//            for (int k = 0; k < _workspace.length; k++)
//            {
//                _workspace[k] = _powerScores.get(index_, k);
//            }

            final int statusIndex = _grid.getNextStatus(index_);

            if (statusIndex == _toStatusOrdinal)
            {
                return 1.0;
            }
            else
            {
                return 0.0;
            }

            //final int offset = _likelihood.ordinalToOffset(statusIndex);
            //final double logLikelihood = _likelihood.logLikelihood(_workspace, offset);
        }

        @Override
        public int size()
        {
            return _grid.size();
        }

    }

}
