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
package edu.columbia.tjw.item.fit;

import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemParameters;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.data.ItemFittingGrid;

/**
 * @param <S> The status type for this grid
 * @param <R> The regressor type for this grid
 * @param <T> The curve type for this grid
 * @author tyler
 */
public final class ParamFittingGrid<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>> extends ItemParamGrid<S, R, T> implements ItemFittingGrid<S, R>
{
    private final ItemFittingGrid<S, R> _grid;

    public ParamFittingGrid(ItemParameters<S, R, T> params_, ItemFittingGrid<S, R> grid_)
    {
        super(params_, grid_);

        _grid = grid_;
    }

    @Override
    public ItemFittingGrid<S, R> getUnderlying()
    {
        return _grid;
    }

    @Override
    public S getFromStatus()
    {
        return _grid.getFromStatus();
    }

    @Override
    public int getNextStatus(int index_)
    {
        return _grid.getNextStatus(index_);
    }
}
