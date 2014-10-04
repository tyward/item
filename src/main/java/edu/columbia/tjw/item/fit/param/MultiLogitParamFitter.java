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
package edu.columbia.tjw.item.fit.param;

import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemFittingGrid;
import edu.columbia.tjw.item.ItemModel;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.ParamFilter;
import edu.columbia.tjw.item.optimize.ConvergenceException;
import java.util.Collection;

/**
 *
 * @author tyler
 * @param <S>
 * @param <R>
 * @param <T>
 */
public class MultiLogitParamFitter<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>> extends ParamFitter<S, R, T>
{
    public MultiLogitParamFitter(final ItemModel<S, R, T> model_)
    {
        super(model_);
    }

    public ItemModel<S, R, T> fit(final ItemFittingGrid<S, R> grid_, final Collection<ParamFilter<S, R, T>> filters_) throws ConvergenceException
    {
        return (ItemModel<S, R, T>) super.fit(grid_, filters_);
    }

}
