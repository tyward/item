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
package edu.columbia.tjw.gsesf;

import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.util.EnumFamily;
import edu.columbia.tjw.item.util.ModularTightPacking;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author tyler
 */
public enum LoanStatus implements ItemStatus<LoanStatus>
{
    TURNOVER('t', 'P', "t"),
    PREPAID('p', 'P', "p"),
    CURRENT('c', 'C', "tpc3"),
    DELINQUENT30('3', '3', "tpc36"),
    DELINQUENT60('6', '6', "c369d"),
    DELINQUENT90('9', '9', "c369frd"),
    FORECLOSURE('f', 'F', "c369frd"),
    REO('r', 'R', "rd"),
    DEFAULTED('d', 'D', "d");

    public static final EnumFamily<LoanStatus> FAMILY = new EnumFamily<>(values());

    private static final ModularTightPacking SHORT_NAME_PACKING;
    private static final LoanStatus[] SHORT_NAME_LOOKUP;
    private static final LoanStatus[] VALUES;

    private final char _shortName;
    private final char _visibleName;
    private final char[] _reachableShortNames;
    private List<LoanStatus> _reachable;
    private List<LoanStatus> _indistinguishable;

    private LoanStatus(final char shortName_, final char visibleName_, final String reachable_)
    {
        _shortName = shortName_;
        _visibleName = visibleName_;
        _reachableShortNames = reachable_.toCharArray();
        _reachable = null;
        _indistinguishable = null;
    }

    public char getShortName()
    {
        return _shortName;
    }

    public char getVisibleName()
    {
        return _visibleName;
    }

    public List<LoanStatus> getIndistinguishable()
    {
        return _indistinguishable;
    }

    public List<LoanStatus> getReachable()
    {
        return _reachable;
    }

    public boolean isReachable(final LoanStatus stat_)
    {
        if (null == stat_)
        {
            throw new NullPointerException("Null loan status: " + stat_);
        }

        return _reachable.contains(stat_);
    }

    public int getReachableCount()
    {
        return _reachable.size();
    }

    public static int count()
    {
        return VALUES.length;
    }

    public static LoanStatus getFromOrdinal(final int ordinal_)
    {
        return VALUES[ordinal_];
    }

    public static LoanStatus getFromShortName(final char shortName_)
    {
        final int shortNameInt = (int) shortName_;
        final int index = SHORT_NAME_PACKING.computeIndex(shortNameInt);
        final LoanStatus stat = SHORT_NAME_LOOKUP[index];

        if (null == stat)
        {
            throw new IllegalArgumentException("Invalid short name: " + shortName_);
        }

        final char checkName = stat.getShortName();

        if (checkName != shortName_)
        {
            throw new IllegalArgumentException("Invalid short name: " + shortName_);
        }

        return stat;
    }

    private void resolveReachable()
    {
        final List<LoanStatus> statList = new ArrayList<>(_reachableShortNames.length);

        for (final char next : _reachableShortNames)
        {
            final LoanStatus nextStat = getFromShortName(next);
            statList.add(nextStat);
        }

        this._reachable = Collections.unmodifiableList(statList);

        final char visibleName = this.getVisibleName();

        final List<LoanStatus> indistinguishable = new ArrayList<>();

        for (int i = 0; i < count(); i++)
        {
            final LoanStatus test = LoanStatus.getFromOrdinal(i);

            if (test.getVisibleName() == visibleName)
            {
                indistinguishable.add(test);
            }
        }

        this._indistinguishable = Collections.unmodifiableList(indistinguishable);
    }

    static
    {
        VALUES = LoanStatus.values();
        final int count = VALUES.length;

        final int[] shortNames = new int[count];

        for (int i = 0; i < count; i++)
        {
            final LoanStatus stat = VALUES[i];
            shortNames[i] = (int) stat.getShortName();
        }

        SHORT_NAME_PACKING = new ModularTightPacking(shortNames);
        SHORT_NAME_LOOKUP = new LoanStatus[SHORT_NAME_PACKING.getArraySize()];

        for (int i = 0; i < count; i++)
        {
            final LoanStatus stat = VALUES[i];
            final int nameInt = (int) stat.getShortName();
            final int index = SHORT_NAME_PACKING.computeIndex(nameInt);

            SHORT_NAME_LOOKUP[index] = stat;
        }

        for (final LoanStatus stat : VALUES)
        {
            stat.resolveReachable();
        }
    }

    @Override
    public EnumFamily<LoanStatus> getFamily()
    {
        return FAMILY;
    }
}
