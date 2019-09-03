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
package edu.columbia.tjw.item.csv;

import edu.columbia.tjw.item.ItemRegressorReader;
import edu.columbia.tjw.item.base.RawReader;
import edu.columbia.tjw.item.base.SimpleRegressor;
import edu.columbia.tjw.item.base.SimpleStatus;
import edu.columbia.tjw.item.data.ItemStatusGrid;
import edu.columbia.tjw.item.util.EnumFamily;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

/**
 * @author tyler
 */
public class RawStatusGrid implements ItemStatusGrid<SimpleStatus, SimpleRegressor>, Serializable
{
    private static final long serialVersionUID = 0x870ca358f9b330e1L;
    final int _startingStatus;
    final int[] _endingStatus;

    final ItemRegressorReader[] _readers;
    final EnumFamily<SimpleStatus> _statFamily;
    final EnumFamily<SimpleRegressor> _regFamily;

    public RawStatusGrid(final SimpleStatus startingStatus_, final List<double[]> data_,
                         final List<SimpleStatus> endingStatus_, final EnumFamily<SimpleStatus> statFamily_,
                         final EnumFamily<SimpleRegressor> regFamily_)
    {
        if (endingStatus_.size() != data_.size())
        {
            throw new IllegalArgumentException("Size mismatch.");
        }

        if (null != startingStatus_)
        {
            _startingStatus = startingStatus_.ordinal();
        }
        else
        {
            _startingStatus = 0;
        }
        _statFamily = statFamily_;
        _regFamily = regFamily_;

        final int rowCount = endingStatus_.size();
        final int regCount = regFamily_.size();
        _endingStatus = new int[rowCount];

        final double[][] gridData = new double[regCount][rowCount];

        for (int i = 0; i < _endingStatus.length; i++)
        {
            _endingStatus[i] = endingStatus_.get(i).ordinal();

            final double[] nextRow = data_.get(i);

            for (int w = 0; w < regCount; w++)
            {
                gridData[w][i] = nextRow[w];
            }
        }

        _readers = new ItemRegressorReader[regCount];

        for (int i = 0; i < regCount; i++)
        {
            _readers[i] = new RawReader(gridData[i]);
        }
    }

    @Override
    public EnumFamily<SimpleStatus> getStatusFamily()
    {
        return _statFamily;
    }

    @Override
    public int getStatus(int index_)
    {
        return _startingStatus;
    }

    @Override
    public int getNextStatus(int index_)
    {
        return _endingStatus[index_];
    }

    @Override
    public boolean hasNextStatus(int index_)
    {
        return true;
    }

    @Override
    public final Set<SimpleRegressor> getAvailableRegressors()
    {
        return getRegressorFamily().getMembers();
    }

    @Override
    public ItemRegressorReader getRegressorReader(SimpleRegressor field_)
    {
        return _readers[field_.ordinal()];
    }

    @Override
    public int size()
    {
        return _endingStatus.length;
    }

    @Override
    public EnumFamily<SimpleRegressor> getRegressorFamily()
    {
        return _regFamily;
    }

}
