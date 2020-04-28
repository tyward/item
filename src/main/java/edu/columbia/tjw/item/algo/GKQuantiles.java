package edu.columbia.tjw.item.algo;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Code cloned from streaminer project, and updated to use more primitives for performance reasons.
 * <p>
 * Original javadoc follows.
 * <p>
 * This class is an implementation of the Greenwald-Khanna algorithm for computing
 * epsilon-approximate quantiles of large data sets. In its pure form it is an offline
 * algorithm. But it is used as a black box by many online algorithms for computing
 * epsilon-approximate quantiles on data streams.<br>
 * Our implementation widely adapts the original idea published by <i>Michael Greenwald
 * </i> and <i>Sanjeev Khanna</i> in their paper <i>"Space-Efficient Online Computation
 * of Quantile Summaries"</i>. Contrary to their idea this implementation uses a list
 * rather than a tree structure to maintain the elements.
 *
 * @author Markus Kokott, Carsten Przyluczky
 */
public class GKQuantiles implements Serializable
{
    private static final long serialVersionUID = 0x3b9df13d1769c5bdL;

    /**
     * This value specifies the error bound.
     */
    private final double _epsilon;

    private List<QuantileBlock> summary;
    private double minimum;
    private double maximum;
    private int stepsUntilMerge;

    /**
     * GK needs 1 / (2 * epsilon) elements to complete it's initial phase
     */
    private boolean initialPhase;
    private int count;


    public GKQuantiles()
    {
        this(0.05);
    }

    /**
     * Creates a new GKQuantiles object that computes epsilon-approximate quantiles.
     *
     * @param epsilon_ The maximum error bound for quantile estimation.
     */
    public GKQuantiles(double epsilon_)
    {
        if (!(epsilon_ > 0 && epsilon_ < 1))
        {
            throw new RuntimeException("Epsilon must be in [0, 1]");
        }

        this._epsilon = epsilon_;
        this.minimum = Double.MAX_VALUE;
        this.maximum = Double.MIN_VALUE;
        double mergingSteps = Math.floor(1.0 / (2.0 * _epsilon));
        this.stepsUntilMerge = (int) mergingSteps;
        this.summary = new ArrayList<QuantileBlock>();
        this.count = 0;
        this.initialPhase = true;
    }

    public void offer(double value)
    {
        insertItem(value);
        incrementCount();
        if (count % stepsUntilMerge == 0 && !initialPhase)
        {
            compress();
        }
    }

    /**
     * Estimates appropriate quantiles (i.e. values that holds epsilon accuracy). Note that if
     * the query parameter doesn't lay in [0,1] <code>Double.NaN</code> is returned! The same
     * result will be returned if an empty instance of GK is queried.
     *
     * @param q a <code>float</code> value
     * @return an estimated quantile represented by a {@link Double}. Will return {@link Double#NaN}
     * if <code>phi</code> isn't between 0 and 1 or this instance of <code>GKQuantiles</code> is empty.
     */
    public double getQuantile(double q)
    {
        /*--------------------------------------------------------
         * special cases if some queries occur in a very early state
         */
        if (count == 0 || q < 0 || q > 1)
        {
            return Double.NaN;
        }
        if (count == 1)
        {
            return minimum;
        }
        if (count == 2)
        {
            if (q < 0.5)
            {
                return minimum;
            }
            if (q >= 0.5)
            {
                return maximum;
            }
        }
        //---------------------------------------------------------


        int wantedRank = (int) (q * count);
        int currentMinRank = 0;
        int currentMaxRank = 0;
        double tolerance = (_epsilon * count);

        // if the wanted range is as most epsilon * count ranks smaller than the maximum the maximum
        // will always be an appropriate estimate
        if (wantedRank > count - tolerance)
        {
            return maximum;
        }

        // if the wanted range is as most epsilon * count ranks greater than the minimum the minimum
        // will always be an appropriate estimate
        if (wantedRank < tolerance)
        {
            return minimum;
        }

        QuantileBlock lastTuple = summary.get(0);

        // usually a range is estimated during this loop. it's element's value will be returned
        for (QuantileBlock nextBlock : summary)
        {
            currentMinRank += nextBlock.getOffset();
            currentMaxRank = currentMinRank + nextBlock.getRange();

            if (currentMaxRank - wantedRank <= tolerance)
            {
                lastTuple = nextBlock;
                if (wantedRank - currentMinRank <= tolerance)
                {

                    return nextBlock.getValue();
                }
            }
        }

        return lastTuple.getValue();
    }


    /**
     * Checks whether <code>item</code> is a new extreme value (i.e. minimum or maximum) or lays between those values
     * and calls the appropriate insert method.
     *
     * @param item {@link Double} value of current element
     */
    private void insertItem(double item)
    {
        if (item < minimum)
        {
            insertAsNewMinimum(item);
            return;
        }

        if (item >= maximum)
        {
            insertAsNewMaximum(item);
            return;
        }

        insertInBetween(item);
    }

    /**
     * This method will be called every time an element arrives whose value is smaller than the value
     * of the current minimum. Contrary to "normal" elements, the minimum's range have to be zero.
     *
     * @param item - new element with a {@link Double} value smaller than the current minimum of the summary.
     */
    private void insertAsNewMinimum(double item)
    {
        minimum = item;
        QuantileBlock newTuple = new QuantileBlock(item, 1, 0);
        summary.add(0, newTuple);
    }

    /**
     * This method will be called every time an element arrives whose value is greater than the value
     * of the current maximum. Contrary to "normal" elements, the maximum's range have to be zero.
     *
     * @param item - new element with a {@link Double} value greater than the current maximum of the summary.
     */
    private void insertAsNewMaximum(double item)
    {
        if (item == maximum)
        {
            QuantileBlock newTuple = new QuantileBlock(item, 1,
                    computeRangeForNewTuple(summary.get(summary.size() - 1)));
            summary.add(summary.size() - 2, newTuple);
        }
        else
        {
            maximum = item;
            QuantileBlock newTuple = new QuantileBlock(item, 1, 0);
            summary.add(newTuple);
        }
    }

    /**
     * Every time a new element gets processed this method is called to insert this element into
     * the summary. During initial phase element's ranges have to be zero. After this phase every
     * new element's range depends on its successor.
     *
     * @param item - a new arrived element represented by a {@link Double} value.
     */
    private void insertInBetween(double item)
    {
        QuantileBlock newTuple = new QuantileBlock(item, 1, 0);

        for (int i = 0; i < summary.size() - 1; i++)
        {
            QuantileBlock current = summary.get(i);
            QuantileBlock next = summary.get(i + 1);

            if (item >= current.getValue() && item < next.getValue())
            {
                // while GK have seen less than 1 / (2*epsilon) elements, all elements must have an
                // offset of 0
                if (!initialPhase)
                {
                    newTuple.setRange(computeRangeForNewTuple(next));
                }

                summary.add(i + 1, newTuple);
                return;
            }
        }
    }

    /**
     * Increments <code>count</code> and ends the initial phase if enough elements have been seen.
     */
    private void incrementCount()
    {
        count++;
        if (count == stepsUntilMerge)
        {
            initialPhase = false;
        }
    }

    /**
     * Due to space efficiency the summary is compressed periodically
     */
    private void compress()
    {
        if (this.summary.size() < 2)
        {
            // We don't compress if there's nothing to compress.
            return;
        }

        List<List<QuantileBlock>> partitions = getPartitionsOfSummary();
        List<QuantileBlock> mergedSummary = new ArrayList<QuantileBlock>();

        // just merge tuples per partition and concatenate the single resulting working sets

        mergedSummary.addAll(partitions.get(partitions.size() - 1));

        for (int i = partitions.size() - 2; i > 0; i--)
        {
            mergedSummary.addAll(mergeWorkingSet(partitions.get(i)));
        }

        mergedSummary.addAll(partitions.get(0));
        mergedSummary = sortWorkingSet(mergedSummary);
        summary = mergedSummary;
    }

    /**
     * merges a whole partition and therefore saves space.
     *
     * @param workingSet a partition (created by {@link #getPartitionsOfSummary()}) or parts of it
     * @return a {@link LinkedList} of {@link QuantileBlock} containing the merged working set.
     */
    private List<QuantileBlock> mergeWorkingSet(List<QuantileBlock> workingSet)
    {
        // recursion stops here
        if (workingSet.size() < 2)
        {
            return workingSet;
        }

        LinkedList<QuantileBlock> mergedWorkingSet = new LinkedList<QuantileBlock>();            // resulting working
        // set
        LinkedList<QuantileBlock> currentWorkingSet = new LinkedList<QuantileBlock>();            // elements for
        // this step of recursion
        LinkedList<QuantileBlock> remainingWorkingSet = new LinkedList<QuantileBlock>();        // remaining elements
        // after this step
        // of recursion
        remainingWorkingSet.addAll(workingSet);

        int index = 1;
        int bandOfChildren = computeBandOfTuple(workingSet.get(0));
        int bandOfParent = computeBandOfTuple(workingSet.get(index));
        currentWorkingSet.add(workingSet.get(0));
        remainingWorkingSet.removeFirst();

        // we are looking for the next tuple that have a greater band than the first element because that
        // element will be the limit for the first element to get merged into
        while (bandOfChildren == bandOfParent && workingSet.size() - 1 > index)
        {
            // the working set will be partitioned into a working set for the current step of recursion and
            // a partition that contains all elements that have to be processed in later steps
            currentWorkingSet.add(workingSet.get(index));
            remainingWorkingSet.remove(workingSet.get(index));

            index++;
            bandOfParent = computeBandOfTuple(workingSet.get(index));
        }
        QuantileBlock parent = workingSet.get(index);

        // there is no real parent. all elements have the same band
        if (bandOfParent == bandOfChildren)
        {
            currentWorkingSet.add(parent);
            mergedWorkingSet.addAll(mergeSiblings(currentWorkingSet));
            return mergedWorkingSet;
        }

        int capacityOfParent = computeCapacityOfTuple(parent);

        // an element can be merged into it's parent if the resulting tuple isn't full (i.e. capacityOfParent > 1
        // after merging)
        while (capacityOfParent > currentWorkingSet.getLast().getOffset() && currentWorkingSet.size() > 1)
        {
            merge(currentWorkingSet.getLast(), parent);
            currentWorkingSet.removeLast();
            capacityOfParent = computeCapacityOfTuple(parent);
        }

        // checking whether all children were merged into parent or some were left over
        if (currentWorkingSet.isEmpty())
        {
            mergedWorkingSet.addAll(mergeWorkingSet(remainingWorkingSet));
        }
        // if there are some children left, some of them can probably be merged into siblings.
        // if there is any child left over, parent can't be merged into any other tuple, so it must be removed
        // from the elements in the remaining working set.
        else
        {
            remainingWorkingSet.remove(parent);
            mergedWorkingSet.addAll(mergeSiblings(currentWorkingSet));
            mergedWorkingSet.add(parent);
            mergedWorkingSet.addAll(mergeWorkingSet(remainingWorkingSet));
        }

        return mergedWorkingSet;
    }

    /**
     * this method merges elements that have the same band
     *
     * @param workingSet - a {@link LinkedList} of {@link QuantileBlock}
     * @return a {@link LinkedList} of {@link QuantileBlock} with smallest possible size in respect to
     * GKs merging operation.
     */
    private LinkedList<QuantileBlock> mergeSiblings(LinkedList<QuantileBlock> workingSet)
    {
        // nothing left to merge
        if (workingSet.size() < 2)
        {
            return workingSet;
        }

        LinkedList<QuantileBlock> mergedSiblings = new LinkedList<QuantileBlock>();

        // it is only possible to merge an element into a sibling, if this sibling is the element's
        // direct neighbor to the right
        QuantileBlock lastSibling = workingSet.getLast();
        workingSet.removeLast();
        boolean canStillMerge = true;

        // as long as the rightmost element can absorb elements, it will absorb his sibling to the left
        while (canStillMerge && !workingSet.isEmpty())
        {
            if (this.areMergeable(workingSet.getLast(), lastSibling))
            {
                merge(workingSet.getLast(), lastSibling);
                workingSet.removeLast();
            }
            else
            {
                canStillMerge = false;
            }
        }
        mergedSiblings.add(lastSibling);

        // recursion
        mergedSiblings.addAll(mergeSiblings(workingSet));

        return mergedSiblings;
    }

    /**
     * call this method to merge the element <code>left</code> into the element <code>right</code>.
     * Please note, that only elements with smaller value and a band not greater than <code>right
     * </code> can be element <code>left</code>.
     *
     * @param left  - element the will be deleted after merging
     * @param right - element that will contain the offset of element <code>left</code> after merging
     */
    private void merge(QuantileBlock left, QuantileBlock right)
    {
        right.setOffset(right.getOffset() + left.getOffset());
    }

    /**
     * The range of an element depends on range and offset of it's succeeding element.
     * This methods computes the current element's range.
     *
     * @return range of current element as {@link Integer} value
     */
    private int computeRangeForNewTuple(QuantileBlock successor)
    {
        if (initialPhase)
        {
            return 0;
        }

        //this is how it's done during algorithm detail in the paper
        double range = 2.0 * _epsilon * count;
        range = Math.floor(range);

        //this is the more adequate version presented at section "empirical measurements"
        int successorRange = successor.getRange();
        int successorOffset = successor.getOffset();
        if (successorRange + successorOffset - 1 >= 0)
        {
            return (successorRange + successorOffset - 1);
        }

        return (int) range;
    }

    /**
     * Partitions a list into {@link LinkedList}s of {@link QuantileBlock}, so that bands of elements
     * in a single {@link LinkedList} are monotonically increasing.
     *
     * @return a {@link LinkedList} containing {@link LinkedList}s of {@link Double} which are
     * the partitions of {@link #summary}
     */
    private List<List<QuantileBlock>> getPartitionsOfSummary()
    {
        List<List<QuantileBlock>> partitions = new LinkedList<List<QuantileBlock>>();
        List<QuantileBlock> workingSet = summary;
        LinkedList<QuantileBlock> currentPartition = new LinkedList<QuantileBlock>();
        QuantileBlock lastTuple;
        QuantileBlock lastButOneTuple;

        // assuring that the minimum and maximum won't appear in a partition with other elements
        QuantileBlock minimum = workingSet.get(0);
        QuantileBlock maximum = workingSet.get(workingSet.size() - 1);
        workingSet.remove(0);
        workingSet.remove(workingSet.size() - 1);

        // adding the minimum as the first element into partitions
        currentPartition = new LinkedList<QuantileBlock>();
        currentPartition.add(minimum);
        partitions.add(currentPartition);
        currentPartition = new LinkedList<QuantileBlock>();

        // nothing left to partitioning
        if (workingSet.size() < 2)
        {
            partitions.add(workingSet);
            // adding the maximum as the very last element into partitions
            currentPartition = new LinkedList<QuantileBlock>();
            currentPartition.add(maximum);
            partitions.add(currentPartition);
            return partitions;
        }

        // we process the working set from the very last element to the very first one
        while (workingSet.size() >= 2)
        {
            lastTuple = workingSet.get(workingSet.size() - 1);
            lastButOneTuple = workingSet.get(workingSet.size() - 2);
            currentPartition.addFirst(lastTuple);

            // every time we find an element whose band is greater than the current one the current partition
            // ended and we have to add a new partition to the resulting list
            if (isPartitionBorder(lastButOneTuple, lastTuple))
            {
                partitions.add(currentPartition);
                currentPartition = new LinkedList<QuantileBlock>();
            }
            else
            {
                // here got's the last element inserted into an partition
                if (workingSet.size() == 2)
                {
                    currentPartition.addFirst(lastButOneTuple);
                }
            }
            workingSet.remove(workingSet.size() - 1);
        }

        partitions.add(currentPartition);

        // adding the maximum as a partition of it's own at the very last position
        currentPartition = new LinkedList<QuantileBlock>();
        currentPartition.add(maximum);
        partitions.add(currentPartition);

        return partitions;
    }

    /**
     * Call this method to get the current capacity of an element.
     *
     * @param tuple - a {@link QuantileBlock}
     * @return {@link Integer} value representing the <code>tuple</code>'s capacity
     */
    private int computeCapacityOfTuple(QuantileBlock tuple)
    {
        int offset = tuple.getOffset();
        double currentMaxCapacity = Math.floor(2.0 * _epsilon * count);
        return (int) (currentMaxCapacity - offset);
    }

    /**
     * A tuple's band depend on the number of seen elements (<code>count</code>) and the
     * tuple's range.
     * <ul>
     * <li> While GK hasn't finished it's initial phase, all elements have to be put into a
     * band of their own. This is done using a band -1.
     * <li> If count and range are logarithmically equal the tuple's band will be 0
     * <li> Else the tuple's band will be a value between 1 and <i>log(2*epsilon*count)</i>
     * </ul>
     * Please refer to the paper if you are interested in the formula for computing bands.
     *
     * @param tuple - a {@link QuantileBlock}
     * @return {@link Integer} value specifying <code>tuple</code>'s band
     */
    private int computeBandOfTuple(QuantileBlock tuple)
    {
        double p = Math.floor(2 * _epsilon * count);

        // this will be true for new tuples
        if (areLogarithmicallyEqual(p, tuple.getRange()))
        {
            return 0;
        }

        // initial phase
        if (tuple.getRange() == 0)
        {
            return -1;
        }

        int alpha = 0;
        double lowerBound = 0d;
        double upperBound = 0d;

        while (alpha < (Math.log(p) / Math.log(2)))
        {
            alpha++;
            int twoPowAlpha = 2 << alpha;
            lowerBound = p - twoPowAlpha - (p % twoPowAlpha);

            if (lowerBound <= tuple.getRange())
            {
                int twoPowAlphaM1 = 2 << (alpha - 1);
                upperBound = p - twoPowAlphaM1 - (p % twoPowAlphaM1);

                if (upperBound >= tuple.getRange())
                {
                    return alpha;
                }
            }
        }

        return alpha;
    }

    /**
     * Checks if two given values are logarithmically equal, i.e. the floored logarithm of
     * <code>valueOne</code> equals the floored logarithm of <code>valueTwo</code>.
     *
     * @param valueOne - a {@link Double} representing a {@link QuantileBlock}s band
     * @param valueTwo - a {@link Double} representing a {@link QuantileBlock}s band
     * @return <code>true</code> if both values are logarithmically equal
     */
    private boolean areLogarithmicallyEqual(double valueOne, double valueTwo)
    {
        return (Math.floor(Math.log(valueOne)) == Math.floor(Math.log(valueTwo)));
    }

    /**
     * To check whether a pair of elements are mergeable or not you should use this method. Its
     * decision takes into account the bands and values of the given elements.
     *
     * @param tuple  The element that will be deleted after merging.
     * @param parent The element that will absorb <code>tuple</code> during merge.
     * @return <code>true</code> if given elements are mergeable or <code>false</code> else.
     */
    private boolean areMergeable(QuantileBlock tuple, QuantileBlock parent)
    {
        int capacityOfParent = computeCapacityOfTuple(parent);

        // return true if parent's capacity suffices to absorb tuple and tuple's band isn't greater than parent's
        if (capacityOfParent > tuple.getOffset() && computeBandOfTuple(parent) >= computeBandOfTuple(tuple))
        {
            return true;
        }

        return false;
    }

    /**
     * Bands of elements in a partition are monotonically increasing from the first to the last element.
     * So a partition border is found if a preceding element has a greater band than the current
     * element. This method checks this condition for given elements.
     *
     * @param left  preceding element.
     * @param right current element.
     * @return <code>true</code> if a partition boarder exists between the given elements or <code>
     * false</code> else.
     */
    private boolean isPartitionBorder(QuantileBlock left, QuantileBlock right)
    {
        if (computeBandOfTuple(left) > computeBandOfTuple(right))
        {
            return true;
        }
        return false;
    }

    /**
     * Sorts a {@link LinkedList} of {@link QuantileBlock}.
     *
     * @param workingSet - partitions of summary as a {@link LinkedList} of {@link QuantileBlock}.
     * @return the given working set in ascending order.
     */
    private static List<QuantileBlock> sortWorkingSet(List<QuantileBlock> workingSet)
    {
        List<QuantileBlock> sortedWorkingSet = new ArrayList<QuantileBlock>();

        while (workingSet.size() > 1)
        {
            QuantileBlock currentMinimum = workingSet.get(0);

            for (int i = 0; i < workingSet.size(); i++)
            {
                if (currentMinimum.getValue() > workingSet.get(i).getValue())
                {
                    currentMinimum = workingSet.get(i);
                }
            }
            workingSet.remove(currentMinimum);

            sortedWorkingSet.add(currentMinimum);
        }

        sortedWorkingSet.add(workingSet.get(0));
        return sortedWorkingSet;
    }

    public int getCount()
    {
        return this.count;
    }


    @Override
    public String toString()
    {
        StringBuffer s = new StringBuffer();
        s.append(getClass().getCanonicalName());
        s.append(" {");
        s.append(" epsilon=");
        s.append(_epsilon);
        s.append(" }");
        return s.toString();
    }

    /**
     * This is just a wrapper class to hold all needed informations of an element. It contains the following
     * informations:
     * <ul>
     * <li><b>value</b>: the value of the element</li>
     * <li><b>offset</b>: the difference between the least rank of this element and the rank of the preceding
     * element.</li>
     * <li><b>range</b>: the span between this elements least and most rank</li>
     * </ul>
     */
    private static final class QuantileBlock implements Serializable
    {
        private static final long serialVersionUID = 0x80ca623569742e28L;
        private final double value;
        private int offset;
        private int range;

        public QuantileBlock(Double value, Integer offset, Integer range)
        {
            this.value = value;
            this.offset = offset;
            this.range = range;
        }

        public double getValue()
        {
            return value;
        }

        public int getOffset()
        {
            return offset;
        }

        public void setOffset(int offset)
        {
            this.offset = offset;
        }

        public int getRange()
        {
            return range;
        }

        public void setRange(int range)
        {
            this.range = range;
        }

        @Override
        public String toString()
        {
            String out = "( " + value + ", " + offset + ", " + range + " )";
            return out;
        }
    }

}
