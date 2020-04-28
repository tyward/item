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

import edu.columbia.tjw.item.util.AbstractEnumMember;
import edu.columbia.tjw.item.util.EnumFamily;
import edu.columbia.tjw.item.util.HashUtil;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Constructs a proper EnumFamily from a collection of strings.
 * <p>
 * It is slightly better to define the EnumMembers as an actual java enum (see
 * BinaryStatus), because that ensures that each family is a singleton. As is,
 * this code makes reasonable attempts to make the family a fairly coherent
 * singleton but it can't be guaranteed.
 *
 * @author tyler
 */
public final class SimpleStringEnum extends AbstractEnumMember<SimpleStringEnum>
{
    private static final int CLASS_HASH = HashUtil.startHash(SimpleStringEnum.class);
    private static final long serialVersionUID = 0x2a04f2981ed75926L;

    private EnumFamily<SimpleStringEnum> _family;

    private SimpleStringEnum(final int ordinal_, final String name_, final int hashBase_)
    {
        super(ordinal_, name_, CLASS_HASH);
    }

    public static EnumFamily<SimpleStringEnum> generateFamily(final Collection<String> regressorNames_)
    {
        if (regressorNames_.isEmpty())
        {
            throw new IllegalArgumentException("Cannot create an empty regressor set.");
        }

        final int size = regressorNames_.size();
        final Set<String> checkSet = new HashSet<>(regressorNames_);

        if (checkSet.size() != size)
        {
            throw new IllegalArgumentException("Regressor names are not distinct.");
        }

        final SimpleStringEnum[] regs = new SimpleStringEnum[size];
        int pointer = 0;
        final int hashBase = checkSet.hashCode();

        for (final String next : regressorNames_)
        {
            final SimpleStringEnum nextReg = new SimpleStringEnum(pointer, next, hashBase);
            regs[pointer++] = nextReg;
        }

        final EnumFamily<SimpleStringEnum> family = EnumFamily.generateFamily(regs, false);

        for (final SimpleStringEnum reg : regs)
        {
            reg.setFamily(family);
        }

        return family;
    }


    @Override
    public EnumFamily<SimpleStringEnum> getFamily()
    {
        return _family;
    }

    private void setFamily(final EnumFamily<SimpleStringEnum> family_)
    {
        _family = family_;
    }

    @Override
    public String toString()
    {
        return "SimpleStringEnum[" + name() + "]";
    }
}
