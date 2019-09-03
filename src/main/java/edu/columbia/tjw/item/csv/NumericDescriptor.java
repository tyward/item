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
package edu.columbia.tjw.item.csv;

import java.io.Serializable;

/**
 * @author tyler
 */
public final class NumericDescriptor implements Serializable
{
    private static final long serialVersionUID = 0x312ffe6686f4d850L;

    private final String _columnName;
    private final boolean _canBeNaN;

    public NumericDescriptor(final String columnName_, final boolean canBeNaN_)
    {
        if (null == columnName_)
        {
            throw new NullPointerException("Column name cannot be null");
        }

        _columnName = columnName_;
        _canBeNaN = canBeNaN_;
    }

    public String getColumnName()
    {
        return _columnName;
    }

    public boolean getCanBeNaN()
    {
        return _canBeNaN;
    }

}
