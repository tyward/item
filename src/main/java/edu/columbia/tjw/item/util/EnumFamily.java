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

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 *
 * This is needed because many of the relevant methods in enum (e.g. values())
 * are static, so they cannot be used against objects of unknown types.
 *
 * @author tyler
 * @param <V> The type of enum that composes this family.
 */
public final class EnumFamily<V extends EnumMember<V>> implements Serializable
{
    private final V[] _members;
    private final SortedSet<V> _memberSet;
    private final Map<String, V> _nameMap;

    /**
     * Initialize a new enum family, should pass it enum.values().
     *
     * @param values_ The output of enum.values() should be given here.
     */
    public EnumFamily(final V[] values_)
    {
        if (values_.length < 1)
        {
            throw new IllegalArgumentException("Values must have positive length.");
        }

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

    /**
     * Returns all members of this enum family.
     *
     * @return Same as enum.values()
     */
    public SortedSet<V> getMembers()
    {
        return _memberSet;
    }

    /**
     * How many members does this enum have.
     *
     * @return enum.values().length
     */
    public int size()
    {
        return _members.length;
    }

    /**
     * Look up an enum by its ordinal
     *
     * @param ordinal_ The ordinal of the enum to retrieve.
     * @return enum.values()[ordinal_]
     */
    public V getFromOrdinal(final int ordinal_)
    {
        return _members[ordinal_];
    }

    /**
     * Look up an enum by name.
     *
     * @param name_ The name of the enum to look up.
     * @return enum.fromName(name_)
     */
    public V getFromName(final String name_)
    {
        return _nameMap.get(name_);
    }

    private Object readResolve()
    {
        //Enforce the singleton condition.
        return this._members[0].getFamily();
    }
}
