package edu.columbia.tjw.item.fit.calculator;

import edu.columbia.tjw.item.algo.DoubleVector;

public interface FitPoint
{
    /**
     * Returns the complete vector of parameters for this point.
     *
     * @return
     */
    DoubleVector getParameters();

    int getBlockSize();

    int getBlockCount();

    int getNextBlock(BlockCalculationType type_);

    void clear();

    int getDimension();

    void computeAll(BlockCalculationType type_, BlockResult prevDerivative_);

    void computeAll(BlockCalculationType type_);

    BlockResult getAggregated(BlockCalculationType type_);

    void computeUntil(int endBlock_, BlockCalculationType type_, BlockResult prevDerivative_);

    void computeUntil(int endBlock_, BlockCalculationType type_);

    BlockResult getBlock(int index_, BlockCalculationType type_);

    int getSize();
}
