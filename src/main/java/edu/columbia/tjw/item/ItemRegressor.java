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

import edu.columbia.tjw.item.util.EnumMember;

/**
 *
 * @author tyler
 * @param <V>
 */
public interface ItemRegressor<V extends ItemRegressor<V>> extends EnumMember<V>
{

    /**
     * One of these regressors represents the intercept term of the model. 
     * 
     * That term is special, we need to be able to find it. 
     * 
     * This regressor should have a constant value, preferably 1.0, but definitely
     * not zero.
     * 
     * @return The regressor corresponding to the intercept term of the model
     */
    public V getIntercept();

}
