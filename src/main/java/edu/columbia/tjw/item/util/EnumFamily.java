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
package edu.columbia.tjw.item.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 *
 * @author tyler
 * @param <V>
 */
public final class EnumFamily<V extends EnumMember<V>>
{
    private final V[] _members;
    private final SortedSet<V> _memberSet;
    private final Map<String, V> _nameMap;

    public EnumFamily(final V[] values_)
    {
        _members = values_;
        _memberSet = Collections.unmodifiableSortedSet(new TreeSet<>(Arrays.asList(values_)));

        if (_members.length != _memberSet.size())
        {
            throw new IllegalArgumentException("Members are not distinct!");
        }

        _nameMap = new HashMap<>();
        int pointer = 0;

        for (final V next : _memberSet)
        {
            if ((_members[pointer].ordinal() != pointer) || (next != _members[pointer++]))
            {
                throw new IllegalArgumentException("Members out of order.");
            }

            _nameMap.put(next.name(), next);
        }

    }

    public SortedSet<V> getMembers()
    {
        return _memberSet;
    }

    public int size()
    {
        return _members.length;
    }

    public V getFromOrdinal(final int ordinal_)
    {
        return _members[ordinal_];
    }

    public V getFromName(final String name_)
    {
        return _nameMap.get(name_);
    }

}
