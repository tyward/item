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

import edu.columbia.tjw.item.util.EnumFamily;

/**
 *
 * @author tyler
 * @param <V>
 */
public interface ItemCurveFactory<V extends ItemCurveType<V>>
{

    /**
     * Generates the curve of the correct type by reading the first N parameters
     * from params_.
     *
     * @param type_
     * @param params_
     * @return
     */
    public ItemCurve<V> generateCurve(final V type_, final double[] params_);

    /**
     *
     * @param mean_
     * @param stdDev_
     * @param params_
     */
    public void fillStartingParameters(final V curveType_, final double mean_, final double stdDev_, final double[] params_);

    /**
     *
     * @return
     */
    public EnumFamily<V> getFamily();

}
