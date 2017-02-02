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
import edu.columbia.tjw.item.ItemCurveParams;
import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemModel;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.algo.QuantileDistribution;

/**
 *
 * @author tyler
 * @param <S> The status type for this param generator
 * @param <R> The regressor type for this param generator
 * @param <T> The curve type for this param generator
 */
public interface ParamGenerator<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
{
    public ItemModel<S, R, T> generatedModel(final double[] params_, final R field_);

    public ItemCurveParams<R, T> generateParams(final double[] params_, final R reg_);

    public double[] getStartingParams(final QuantileDistribution dist_, final R reg_);

    public int paramCount();

    /**
     * Translates from the parameter number (as known by the underlying
     * transformation) to the parameter number corresponding in the params
     * passed to this generator.
     *
     * @param input_ The param number to translate
     * @return The translated param number
     */
    public int translateParamNumber(final int input_);

}
