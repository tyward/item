package edu.columbia.tjw.item.fit.calculator;

public interface FitPoint
{
    int getBlockSize();

    int getBlockCount();

    int getNextBlock(BlockCalculationType type_);

    void computeAll(BlockCalculationType type_);

    BlockResult getAggregated(BlockCalculationType type_);

    void computeUntil(int endBlock_, BlockCalculationType type_);

    BlockResult getBlock(int index_, BlockCalculationType type_);

    int getSize();
}
