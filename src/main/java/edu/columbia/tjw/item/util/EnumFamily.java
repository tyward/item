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

import edu.columbia.tjw.item.util.random.RandomTool;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.WeakHashMap;

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
    //private static final long serialVersionUID = 0x461722f9fc29060cL;
    private static final long serialVersionUID = 2720474101494526203L;

    // This map last forever, mapping the class name to a GUID for the family.
    // This also serves as the internal state mutex.
    private static final Map<String, String> CLASS_MAP = new HashMap<>();

    // This is a weak hash map, allowing items to be garbage collected when no longer 
    // needed , but enforcing uniformity across instances otherwise.
    private static final WeakHashMap<String, WeakReference<EnumFamily>> GUID_MAP = new WeakHashMap<>();

    private final V[] _members;
    private final SortedSet<V> _memberSet;
    private final Map<String, V> _nameMap;
    private final Class<? extends V> _componentClass;
    private final boolean _distinctFamily;
    private final String _familyGUID;

    public EnumFamily(final V[] values_)
    {
        this(values_, true);
    }

    /**
     * Initialize a new enum family, should pass it enum.values().
     *
     * @param values_ The output of enum.values() should be given here.
     * @param distinctFamily_ True if this class should only have one associated
     * EnumFamily.
     */
    @SuppressWarnings("unchecked")
    public EnumFamily(final V[] values_, final boolean distinctFamily_)
    {
        if (values_.length < 1)
        {
            throw new IllegalArgumentException("Values must have positive length.");
        }
        for (final V value : values_)
        {
            if (null == value)
            {
                throw new NullPointerException("Enum members cannot be null.");
            }
        }

        _distinctFamily = distinctFamily_;
        _members = values_.clone();
        _memberSet = Collections.unmodifiableSortedSet(new TreeSet<>(Arrays.asList(_members)));

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

        //we actually know that this cast is valid, provided values is actually of type V. 
        _componentClass = (Class<? extends V>) _members[0].getClass();

        if (distinctFamily_)
        {
            this._familyGUID = _componentClass.getName();
            registerFamilyForClass(_componentClass, this);
        }
        else
        {
            this._familyGUID = RandomTool.randomString(16);
            registerFamily(this);
        }

    }

    /**
     * Returns the class of the component members of this family.
     *
     * @return The class of the members of this family.
     */
    public Class<? extends V> getComponentType()
    {
        return _componentClass;
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
        if (this._distinctFamily)
        {
            synchronized (CLASS_MAP)
            {
                final EnumFamily<?> existing = getFamilyFromClass(this._componentClass, false);

                if (null != existing)
                {
                    return existing;
                }

                registerFamilyForClass(_componentClass, this);
                return this;

            }
        }
        else
        {
            synchronized (CLASS_MAP)
            {
                final EnumFamily<?> existing = lookupFamily(_familyGUID);

                if (null != existing)
                {
                    return existing;
                }

                registerFamily(this);
                return this;
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <V extends EnumMember<V>> EnumFamily<V> getFamilyFromClass(final Class<? extends V> familyClass_, final boolean throwOnMissing_)
    {
        synchronized (CLASS_MAP)
        {
            final String guid = CLASS_MAP.get(familyClass_.getName());

            if (null == guid && throwOnMissing_)
            {
                throw new IllegalArgumentException("No family for class: " + familyClass_);
            }

            final EnumFamily<V> family = (EnumFamily<V>) lookupFamily(guid);

            if (null == family && throwOnMissing_)
            {
                throw new IllegalArgumentException("No family for class: " + familyClass_);
            }

            return family;
        }
    }

    private static <V extends EnumMember<V>> void registerFamilyForClass(final Class<? extends V> familyClass_, final EnumFamily<V> family_)
    {
        if (null == familyClass_ || null == family_)
        {
            throw new NullPointerException("Cannot be null.");
        }

        final String className = familyClass_.getName();

        synchronized (CLASS_MAP)
        {
            if (CLASS_MAP.containsKey(className))
            {
                throw new IllegalArgumentException("Attempt to redefine an enum family.");
            }

            registerFamily(family_);
            CLASS_MAP.put(className, family_._familyGUID);
        }
    }

    private static EnumFamily<?> lookupFamily(final String guid_)
    {
        synchronized (CLASS_MAP)
        {
            final WeakReference<EnumFamily> ref = GUID_MAP.get(guid_);

            if (null == ref)
            {
                return null;
            }

            return ref.get();
        }
    }

    private static void registerFamily(final EnumFamily<?> family_)
    {
        synchronized (CLASS_MAP)
        {
            final WeakReference<EnumFamily> ref = GUID_MAP.get(family_._familyGUID);

            if (null == ref)
            {
                GUID_MAP.put(family_._familyGUID, new WeakReference<>(family_));
                return;
            }

            final EnumFamily current = ref.get();

            if (null == current)
            {
                GUID_MAP.put(family_._familyGUID, new WeakReference<>(family_));
                return;
            }

            throw new IllegalStateException("Family already exists: " + family_._familyGUID);
        }
    }

    /**
     * Generates a new array of the type of the enum members with the given
     * size.
     *
     * All elements are initially set to null.
     *
     * @param size_ The size of the returned array.
     * @return A new empty array of type V with size size_
     */
    public V[] generateTypedArray(final int size_)
    {
        final V[] output = Arrays.copyOf(_members, size_);
        Arrays.fill(output, null);
        return output;
    }

}
