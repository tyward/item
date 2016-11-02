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
package edu.columbia.tjw.gsesf.types;

/**
 * This matches what is written in the constants table...
 *
 * @author tyler
 */
public enum LoanVendor
{
    FANNIE("Fannie Mae", 0),
    FREDDIE("Freddie Mac", 1);

    private final int _id;

    private final String _name;

    private LoanVendor(final String name_, final int id_)
    {
        _name = name_;
        _id = id_;
    }

    public final int getId()
    {
        return _id;
    }

    public final String getName()
    {
        return _name;
    }

}
