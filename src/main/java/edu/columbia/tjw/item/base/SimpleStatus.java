/*
 * Copyright 2017 tyler.
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
package edu.columbia.tjw.item.base;

import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.util.AbstractEnumMember;
import edu.columbia.tjw.item.util.EnumFamily;

import java.util.*;

/**
 * This is a simple fully connected status family generated from a set of names.
 *
 * @author tyler
 */
public class SimpleStatus extends AbstractEnumMember<SimpleStatus> implements ItemStatus<SimpleStatus>
{
    private static final long serialVersionUID = 0x66aa83672f316542L;

    private final SimpleStringEnum _base;
    private final List<SimpleStatus> _indistinguishable;

    //These two must be filled out after construction.
    private EnumFamily<SimpleStatus> _family;
    private List<SimpleStatus> _reachable = null;

    private SimpleStatus(final SimpleStringEnum base_)
    {
        super(base_.ordinal(), base_.name(), base_.hashCode());
        _base = base_;
        _indistinguishable = Collections.singletonList(this);
    }

    /**
     * Constructs a simple status family from the given enum family. The generated family matches names and ordinals
     * exactly, but has lost any other information.
     *
     * @param underlying_
     * @return
     */
    public static <V extends ItemStatus<V>> EnumFamily<SimpleStatus> generateFamily(final EnumFamily<V> underlying_,
                                                                                    final Set<V> allowed_)
    {
        final List<String> names = new ArrayList<>(underlying_.size());

        for (final V next : underlying_.getMembers())
        {
            names.add(next.name());
        }

        EnumFamily<SimpleStatus> output = generateFamily(names);

        for (SimpleStatus next : output.getMembers())
        {
            final String nextName = next.name();
            final V orig = underlying_.getFromName(nextName);

            List<SimpleStatus> reachable = new ArrayList<>();

            for (final V nextReachable : orig.getReachable())
            {
                if (!allowed_.contains(nextReachable))
                {
                    continue;
                }

                reachable.add(output.getFromName(nextReachable.name()));
            }

            next._reachable = Collections.unmodifiableList(reachable);
        }

        return output;
    }

    public static EnumFamily<SimpleStatus> generateFamily(final Collection<String> statusNames_)
    {
        final EnumFamily<SimpleStringEnum> baseFamily = SimpleStringEnum.generateFamily(statusNames_);

        final SimpleStatus[] regs = new SimpleStatus[baseFamily.size()];
        int pointer = 0;

        for (final SimpleStringEnum next : baseFamily.getMembers())
        {
            final SimpleStatus nextReg = new SimpleStatus(next);
            regs[pointer++] = nextReg;
        }

        final EnumFamily<SimpleStatus> family = EnumFamily.generateFamily(regs, false);

        for (final SimpleStatus reg : regs)
        {
            reg.setFamily(family);
        }

        return family;
    }

    @Override
    public EnumFamily<SimpleStatus> getFamily()
    {
        return _family;
    }

    private void setFamily(final EnumFamily<SimpleStatus> family_)
    {
        _family = family_;
    }

    /**
     * Assumes that each status is reachable from every other status.
     *
     * @return
     */
    @Override
    public int getReachableCount()
    {
        return getReachable().size();
    }

    /**
     * Assumes that there are no indistinguishable states, every status can be
     * identified in the data set.
     *
     * @return
     */
    @Override
    public List<SimpleStatus> getIndistinguishable()
    {
        return _indistinguishable;
    }

    @Override
    public List<SimpleStatus> getReachable()
    {
        if (null == _reachable)
        {
            _reachable = Collections.unmodifiableList(new ArrayList<>(_family.getMembers()));
        }

        return _reachable;
    }

    @Override
    public String toString()
    {
        return "SimpleStatus[" + name() + "]";
    }
}
