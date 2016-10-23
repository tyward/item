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

import edu.columbia.tjw.item.algo.QuantileDistribution;
import edu.columbia.tjw.item.util.EnumFamily;
import java.util.Random;

/**
 * A factory designed to generate curves on underlying variables.
 *
 * @author tyler
 * @param <V> The type of curve generated by this factory.
 */
public interface ItemCurveFactory<V extends ItemCurveType<V>>
{

    /**
     * Generates the curve of the correct type by reading the first N parameters
     * from params_.
     *
     * @param params_ Params describing the curve to be generated
     * @return The curve specified by the params
     */
    public ItemCurve<V> generateCurve(final ItemCurveParams<V> params_);

    /**
     * Generates a reasonable parameter starting point given the mean and stdDev
     * of the regressor.
     *
     *
     *
     * @param curveType_ The type of curve to be parameterized
     * @param dist_ The approximate quantiles for this curve
     * @param rand_ A random number generator
     * @return A reasonable set of parameters for this curve
     */
    public ItemCurveParams<V> generateStartingParameters(final V curveType_, final QuantileDistribution dist_, final Random rand_);

    /**
     * Returns the family describing the curves generated by the factory.
     *
     * @return the family describing the curves generated by the factory.
     */
    public EnumFamily<V> getFamily();

}
