package edu.columbia.tjw.item.fit.calculator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FitPoint
{
    private final List<EntropyAnalysis> _analysisList;

    public FitPoint(final List<EntropyAnalysis> analysisList_)
    {
        _analysisList = Collections.unmodifiableList(new ArrayList<>(analysisList_));
    }


}
