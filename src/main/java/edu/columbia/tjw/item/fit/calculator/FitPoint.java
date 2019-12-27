package edu.columbia.tjw.item.fit.calculator;

import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemStatus;

public interface FitPoint
{
    int getBlockSize();

    int getBlockCount();

    int getNextBlock(BlockCalculationType type_);

    void computeAll(BlockCalculationType type_);

    BlockResult getAggregated(BlockCalculationType type_);

    void computeUntil(int endBlock_, BlockCalculationType type_);

    BlockResult getBlock(int index_, BlockCalculationType type_);

    double getMean(int boundary_);

    double getStdDev(int boundary_);

    int getSize();
}
