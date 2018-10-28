package edu.columbia.tjw.item.fit.calculator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FitPoint
{
    private final List<BlockResult> _analysisList;

    public FitPoint(final List<BlockResult> analysisList_)
    {
        _analysisList = Collections.unmodifiableList(new ArrayList<>(analysisList_));
    }


}
