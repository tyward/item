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

/**
 * A grid of data needed by the model.
 *
 * This grid would have enough data to perform projection, additional
 * information is needed for fitting.
 *
 *
 * @author tyler
 * @param <S> The status type for this model
 * @param <R> The regressor type for this model
 */
public interface ItemModelGrid<S extends ItemStatus<S>, R extends ItemRegressor<R>>
{
    public void getRegressors(final int index_, final double[] output_);

    public int totalSize();

    public R getField(final int fieldIndex_);

    public ItemCurve<?> getTransformation(final int fieldIndex_);
}
