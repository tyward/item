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
import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.ParamFilter;

/**
 *
 * @author tyler
 */
public final class CurveFilter<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>> implements ParamFilter<S, R, T>
{
    private final S _fromStatus;
    private final S _toStatus;
    private final R _field;
    private final ItemCurve<T> _trans;

    public CurveFilter(final S fromStatus_, final S toStatus_, final R field_, final ItemCurve<T> trans_)
    {
        _fromStatus = fromStatus_;
        _toStatus = toStatus_;
        _field = field_;
        _trans = trans_;
    }

    public R relatedRegressor()
    {
        return _field;
    }

    @Override
    public boolean isFiltered(S fromStatus_, S toStatus_, R field_, ItemCurve<T> trans_)
    {
        if (fromStatus_ != _fromStatus)
        {
            //not the same model, ignore.
            return false;
        }
        if (field_ != _field)
        {
            //not the same field, ignore.
            return false;
        }
        if (null == _trans)
        {
            //No fitting is allowed on raw regressors once we have curves on them.
            return true;
        }
        if (!_trans.equals(trans_))
        {
            //someone else's curve, leave it up to them to filter it.
            return false;
        }
        if (toStatus_ != _toStatus)
        {
            //Curves are good for only one from -> to pair.
            return true;
        }
        //OK, we're good, everything matches.
        return false;
    }

}
