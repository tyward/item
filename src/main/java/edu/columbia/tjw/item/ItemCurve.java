/*
 * Copyright 2014 tyler.
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
 */
package edu.columbia.tjw.item;

/**
 *
 * @author tyler
 * @param <V>
 */
public interface ItemCurve<V extends ItemCurveType<V>>
{

    /**
     * Applies this curve to x.
     *
     * @param x_
     * @return
     */
    public double transform(final double x_);

    /**
     * Gets the specified parameter of this curve.
     *
     * @param index_
     * @return
     */
    public double getParam(final int index_);

    /**
     * Computes the derivative of this curve with respect to the specified
     * parameter, at x_
     *
     * @param index_
     * @param x_
     * @return
     */
    public double derivative(int index_, double x_);

    /**
     * Gets the type of this curve.
     *
     * @return
     */
    public V getCurveType();

}
