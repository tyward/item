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

import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.util.AbstractEnumMember;
import edu.columbia.tjw.item.util.EnumFamily;
import edu.columbia.tjw.item.util.HashUtil;

import java.io.ObjectStreamException;
import java.util.*;

/**
 * This is a simple regressor. Ideally, it's slightly cleaner to make your own
 * class for each distinct set of regressors, and that class should be an enum,
 * as that will prevent various errors related to regressor redefinition.
 * However, for simplicity, this will make a regressor set from a collection of
 * strings. Ordering of the collection matters (it determines ordering of the
 * results).
 * <p>
 * One of these regressors (of your choice) will be used as an intercept term
 * later on in the modeling process, so be sure to add such a term if one isn't
 * already present.
 *
 * @author tyler
 */
public final class SimpleRegressor extends AbstractEnumMember<SimpleRegressor> implements ItemRegressor<SimpleRegressor>
{
    private static final int CLASS_HASH = HashUtil.startHash(SimpleRegressor.class);
    private static final long serialVersionUID = 0x5d7e6fbd7c93394fL;

    private SimpleStringEnum _base;
    private EnumFamily<SimpleRegressor> _family;

    private SimpleRegressor(final SimpleStringEnum base_)
    {
        super(base_.ordinal(), base_.name(), base_.hashCode());
        _base = base_;
    }

    /**
     * Constructs a simple regressor family from the given enum family. The generated family matches names and ordinals
     * exactly, but has lost any other information.
     *
     * @param underlying_
     * @return
     */
    public static <V extends ItemRegressor<V>> EnumFamily<SimpleRegressor> generateFamily(
            final EnumFamily<V> underlying_,
            final Set<V> allowed_)
    {
        final List<String> names = new ArrayList<>(underlying_.size());

        for (final V next : underlying_.getMembers())
        {
//            if (!allowed_.contains(next))
//            {
//                continue;
//            }
            names.add(next.name());
        }

        return generateFamily(names);
    }

    public static EnumFamily<SimpleRegressor> generateFamily(final Collection<String> regressorNames_)
    {
        final EnumFamily<SimpleStringEnum> baseFamily = SimpleStringEnum.generateFamily(regressorNames_);

        final SimpleRegressor[] regs = new SimpleRegressor[baseFamily.size()];
        int pointer = 0;

        for (final SimpleStringEnum next : baseFamily.getMembers())
        {
            final SimpleRegressor nextReg = new SimpleRegressor(next);
            regs[pointer++] = nextReg;
        }

        final EnumFamily<SimpleRegressor> family = EnumFamily.generateFamily(regs, false);

        for (final SimpleRegressor reg : regs)
        {
            reg.setFamily(family);
        }

        return family;
    }

    @Override
    public EnumFamily<SimpleRegressor> getFamily()
    {
        return _family;
    }

    private void setFamily(final EnumFamily<SimpleRegressor> family_)
    {
        _family = family_;
    }

    @Override
    public String toString()
    {
        return "SimpleRegressor[" + name() + "]";
    }

    private static final Map<SimpleStringEnum, SimpleRegressor> CANONICAL = new HashMap<>();

    private Object readResolve() throws ObjectStreamException
    {
        // First make sure our string enum is canonical.
        _base = EnumFamily.canonicalize(_base.getFamily()).getFromOrdinal(_base.ordinal());

        // Now we make sure that this object is canonical.
        SimpleRegressor member = this;

        synchronized (CANONICAL)
        {
            if (CANONICAL.containsKey(this._base))
            {
                member = CANONICAL.get(this._base);
            }
            else
            {
                CANONICAL.put(this._base, this);
            }
        }

        return member;
    }
}
