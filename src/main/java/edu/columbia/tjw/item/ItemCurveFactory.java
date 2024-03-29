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

import edu.columbia.tjw.item.algo.QuantileStatistics;
import edu.columbia.tjw.item.util.EnumFamily;
import org.apache.commons.math3.random.RandomGenerator;

import java.io.Serializable;
import java.util.Random;

/**
 * A factory designed to generate curves on underlying variables.
 *
 * @param <R>
 * @param <T> The type of curve generated by this factory.
 * @author tyler
 */
public interface ItemCurveFactory<R extends ItemRegressor<R>, T extends ItemCurveType<T>> extends Serializable
{

    /**
     * Most curves should have a notion of the "center" of the curve. Often, we
     * won't want this center to be wildly outside of the bulk of the dataset.
     * Therefore, this function allows us to force the center of the curve into
     * a reasonable range.
     *
     * @param inputCurve_ The curve to bound.
     * @param lowerBound_ The lower bound for the center of the curve.
     * @param upperBound_ The upper bound for the center of the curve.
     * @return A curve with center bounded within the given range, possibly the
     * original curve if it needed no adjustment.
     */
    public ItemCurve<T> boundCentrality(final ItemCurve<T> inputCurve_, final double lowerBound_,
                                        final double upperBound_);

    /**
     * Generates a curve of the proper type by reading the next batch of params
     * from params_
     *
     * @param type_
     * @param offset_
     * @param params_
     * @return
     */
    public ItemCurve<T> generateCurve(final T type_, final int offset_, final double[] params_);

    /**
     * Generates a reasonable parameter starting point given the mean and stdDev
     * of the regressor.
     *
     * @param curveType_ The type of curve to be parameterized
     * @param field_
     * @param dist_      The approximate quantiles for this curve
     * @param rand_      A random number generator
     * @return A reasonable set of parameters for this curve
     */
    public ItemCurveParams<R, T> generateStartingParameters(final T curveType_, final R field_,
                                                            final QuantileStatistics dist_, final RandomGenerator rand_);

    /**
     * Returns the family describing the curves generated by the factory.
     *
     * @return the family describing the curves generated by the factory.
     */
    public EnumFamily<T> getFamily();

}
