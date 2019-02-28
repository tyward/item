package edu.columbia.tjw.item.fit.calculator;

import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemStatus;

public interface FitPoint
{
    int getBlockSize();

    int getBlockCount();

    int getNextBlock();

    void computeAll();

    BlockResult getAggregated();

    void computeUntil(int endBlock_);

    BlockResult getBlock(int index_);

    double getMean(int boundary_);

    double getStdDev(int boundary_);

    int getSize();
}
