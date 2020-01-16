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

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.*;

/**
 * This is needed because many of the relevant methods in enum (e.g. values())
 * are static, so they cannot be used against objects of unknown types.
 *
 * @param <V> The type of enum that composes this family.
 * @author tyler
 */
public final class EnumFamily<V extends EnumMember<V>> implements Serializable
{
    private static final long serialVersionUID = 2720474101494526203L;

    private final V[] _members;
    private final SortedSet<V> _memberSet;
    private final Map<String, V> _nameMap;
    private final Class<? extends V> _componentClass;
    private final boolean _distinctFamily;
    private final String _familyGUID;

    public static <V extends EnumMember<V>> EnumFamily<V> generateFamily(final V[] values_)
    {
        return generateFamily(values_, true);
    }

    /**
     * Initialize a new enum family, should pass it enum.values().
     *
     * @param values_         The output of enum.values() should be given here.
     * @param distinctFamily_ True if this class should only have one associated
     *                        EnumFamily.
     */
    @SuppressWarnings("unchecked")
    public static <V extends EnumMember<V>> EnumFamily<V> generateFamily(final V[] values_,
                                                                         final boolean distinctFamily_)
    {
        synchronized (EnumFamilyRegistry.class)
        {
            if (values_.length < 1)
            {
                throw new IllegalArgumentException("Values must have positive length.");
            }

            final SortedSet<V> memberSet = Collections.unmodifiableSortedSet(new TreeSet<>(Arrays.asList(values_)));

            if (values_.length != memberSet.size())
            {
                throw new IllegalArgumentException("Members are not distinct!");
            }

            final Class<? extends V> componentClass = (Class<? extends V>) values_[0].getClass();

            for (final V next : values_)
            {
                if (next.getClass() != componentClass)
                {
                    throw new ClassCastException("Members must all be the same class.");
                }
            }

            final String familyGuid;
            final HashTool tool = new HashTool();
            tool.updateString(componentClass.getName());

            if (distinctFamily_)
            {
                familyGuid = tool.doHashString();
            }
            else
            {
//                if (!AbstractEnumMember.class.isAssignableFrom(componentClass))
//                {
//                    throw new ClassCastException(
//                            "Non distinct families must descen from " + AbstractEnumMember.class.getName());
//                }

                for (final V member : memberSet)
                {
                    tool.updateString(member.name());
                    tool.updateLong(member.hashCode());
                }

                // If we have the same elements in the same order for the same class, this family will be viewed as
                // identical.
                familyGuid = tool.doHashString();
            }


            final EnumFamily<V> existing = EnumFamilyRegistry.lookupFamily(familyGuid, componentClass);

            if (null != existing)
            {
                return existing;
            }

            return new EnumFamily<>(values_, memberSet, componentClass, familyGuid, distinctFamily_);
        }
    }


    private EnumFamily(final V[] values_, final SortedSet<V> memberSet_,
                       final Class<? extends V> componentClass_, String familyGuid_, final boolean distinctFamily_)
    {
        _distinctFamily = distinctFamily_;
        _members = values_.clone();
        _memberSet = memberSet_;
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
        _componentClass = componentClass_;
        this._familyGUID = familyGuid_;

        EnumFamilyRegistry.registerFamily(this);
    }

    @SuppressWarnings("unchecked")
    public <W extends EnumMember<W>> EnumFamily<W> castFamily(final Class<? extends W> componentClass_)
    {
        if (_componentClass == componentClass_)
        {
            return (EnumFamily<W>) this;
        }

        throw new ClassCastException("Improper cast: " + componentClass_ + " != " + _componentClass);
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


    /**
     * Generates a new array of the type of the enum members with the given
     * size.
     * <p>
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

    public static <W extends EnumMember<W>> EnumFamily<W> canonicalize(final EnumFamily<W> family_)
    {
        synchronized (EnumFamilyRegistry.class)
        {
            EnumFamily<W> canonical = EnumFamilyRegistry.lookupFamily(family_._familyGUID, family_._componentClass);

            if (null != canonical)
            {
                return canonical;
            }

            EnumFamilyRegistry.registerFamily(family_);
            return family_;
        }
    }

//    // TODO: Remove this, it's no longer needed.
//    private Object readResolve()
//    {
//        return canonicalize(this);
//    }

//    private Object writeReplace() throws ObjectStreamException
//    {
//        return new WriteStub<>(this);
//    }

    private Object readResolve() throws ObjectStreamException
    {
        if (null == _familyGUID)
        {
            return EnumFamily.generateFamily(this._members, this._distinctFamily);
            //throw new IllegalStateException("This should not be possible.");
        }

        final EnumFamily<V> existing = EnumFamilyRegistry.lookupFamily(_familyGUID, _componentClass);

        if (null != existing)
        {
            return existing;
        }

        EnumFamilyRegistry.registerFamily(this);
        return this;
    }

    private static final class EnumFamilyRegistry
    {
        // This is a weak hash map, allowing items to be garbage collected when no longer
        // needed , but enforcing uniformity across instances otherwise.
        private static final WeakHashMap<String, WeakReference<EnumFamily<?>>> GUID_MAP = new WeakHashMap<>();

        private static synchronized void registerFamily(final EnumFamily<?> family_)
        {
            final WeakReference<EnumFamily<?>> ref = GUID_MAP.get(family_._familyGUID);

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

            if (current == family_)
            {
                // Nothing to do here.
                return;
            }

            throw new IllegalStateException("Family already exists: " + family_._familyGUID);
        }

        private static synchronized <W extends EnumMember<W>> EnumFamily<W> lookupFamily(final String guid_,
                                                                                         Class<? extends W> componentClass_)
        {
            final WeakReference<EnumFamily<?>> ref = GUID_MAP.get(guid_);

            if (null == ref)
            {
                return null;
            }

            EnumFamily<?> family = ref.get();

            if (null == family)
            {
                GUID_MAP.remove(guid_);
                return null;
            }

            return family.castFamily(componentClass_);
        }
    }

}
