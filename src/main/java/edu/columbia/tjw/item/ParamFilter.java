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
package edu.columbia.tjw.item;

import java.io.Serializable;

/**
 * A filter that can be used to exclude certain coefficients and regressors from
 * fitting.
 *
 * Any item excluded here will be skipped by the curve drawing. If a coefficient
 * already exists for this item, it will be ignored (left unchanged) by the
 * coefficient fitting.
 *
 *
 * @author tyler
 * @param <S> The status type for this model
 * @param <R> The regressor type for this model
 * @param <T> The curve type for this model
 */
public interface ParamFilter<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>> extends Serializable
{

    /**
     * The associated regressor for this filter, or null.
     *
     * Should be set only if this filter is to be dropped when the regressor is
     * dropped. Typically the case for filters generated during curve drawing.
     *
     * @return The regressor this filter will filter on (or null, if none)
     */
    public R relatedRegressor();

    /**
     * True if this item is to be ignored.
     *
     * Coefficients are described by the tuple (from, to, field, transformation)
     * to coeff
     *
     * This filter allows some of these tuples to be ignored.
     *
     * @param fromStatus_ Status of the model in question
     * @param toStatus_ Status of this transition 
     * @param field_ The regressor
     * @param trans_ The transformation to be applied, null if no
     * transformation.
     * @return True if this coefficient should not be adjusted.
     */
    public boolean isFiltered(final S fromStatus_, final S toStatus_, final R field_, final ItemCurve<T> trans_);

}
