package edu.columbia.tjw.item.algo;

import edu.columbia.tjw.item.ItemRegressorReader;

public interface QuantileBreakdown
{
    int getSize();

    int findBucket(double x_);

    int firstStep(double alpha_);

    int lastStep(double alpha_);

    double[] getXValues();

    double getMean();

    double getBucketMean(final int index_);

    int getTotalCount();

    double getMeanStdDev();

    QuantileBreakdown rebucket(final int bucketCount_);

    static QuantileBreakdown buildApproximation(final ItemRegressorReader xReader_)
    {
        return new GKQuantileBreakdown(xReader_);
    }
}
