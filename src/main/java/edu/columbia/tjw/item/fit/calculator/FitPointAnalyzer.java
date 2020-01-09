package edu.columbia.tjw.item.fit.calculator;

import edu.columbia.tjw.item.algo.VarianceCalculator;
import edu.columbia.tjw.item.optimize.OptimizationTarget;
import edu.columbia.tjw.item.util.MathTools;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;

public final class FitPointAnalyzer
{
    private static final double EPSILON = Math.ulp(4.0); // Just a bit bigger than machine epsilon.

    private final int _superBlockSize;
    private final double _minStdDev;
    private final OptimizationTarget _target;

    public FitPointAnalyzer(final int superBlockSize_, final double minStdDev_, final OptimizationTarget target_)
    {
        _superBlockSize = superBlockSize_;
        _minStdDev = minStdDev_;
        _target = target_;
    }

    public double compare(final FitPoint a_, final FitPoint b_)
    {
        return compare(a_, b_, _minStdDev);
    }

    public double getSigmaTarget()
    {
        return _minStdDev;
    }

    public double[] getDerivative(final FitPoint point_)
    {
        switch (_target)
        {
            case ENTROPY:
            {
                point_.computeAll(BlockCalculationType.FIRST_DERIVATIVE);
                final BlockResult aggregated = point_.getAggregated(BlockCalculationType.FIRST_DERIVATIVE);
                return aggregated.getDerivative();
            }
            case TIC:
            {
                // There is no realistic way to compute this efficiently, just fall through and use the ICE
                // derivatives instead.
            }
            case ICE:
            case ICE2:
            {
                point_.computeAll(BlockCalculationType.SECOND_DERIVATIVE);
                final BlockResult aggregated = point_.getAggregated(BlockCalculationType.SECOND_DERIVATIVE);

                final int dimension = aggregated.getDerivativeDimension();
                final double[] entropyDerivative = aggregated.getDerivative();
                final double[] extraDerivative = new double[dimension];

                // TODO: Fix this, we have no way to get this number here, so hard coding it.
                final double logM = Math.log(3) * point_.getSize();
                //final double iceBalance = 1.0 / (logM + _params.getEffectiveParamCount());
                final double iceBalance = 1.0 / logM;

                double iTermMax = 0.0;

                for (int i = 0; i < dimension; i++)
                {
                    iTermMax = Math.max(iTermMax, Math.abs(aggregated.getD2Entry(i)));
                }

                if (iTermMax == 0.0)
                {
                    return entropyDerivative;
                }

                final double iTermCutoff = iTermMax * EPSILON;

                for (int i = 0; i < dimension; i++)
                {
                    final double numerator = aggregated.getShiftGradientEntry(i);

                    if (numerator < iTermCutoff)
                    {
                        // The adjustment here is zero....
                        continue;
                    }

                    final double iTerm = aggregated.getD2Entry(i); // Already squared, this one is.

                    if (iTerm < iTermCutoff)
                    {
                        // This particular term is irrelevant, its gradient is basically zero so just skip it.
                        continue;
                    }

                    final double jTerm = aggregated.getJDiagEntry(i);
                    final double denominator = (Math.max(jTerm, 0) * (1.0 - iceBalance) + iTerm * iceBalance);

                    // Assume that the third (and neglected) term roughly cancels one of these two terms, eliminating
                    // the 2.0.
                    extraDerivative[i] = (numerator / denominator) / point_.getSize();
                }

                final double cosCheck = MathTools.cos(extraDerivative, entropyDerivative);
                final double magDiff = MathTools.magnitude(extraDerivative) / MathTools.magnitude(entropyDerivative);

                System.out.println("Gradient comparison[" + magDiff + "]: " + cosCheck);

                for (int i = 0; i < dimension; i++)
                {
                    entropyDerivative[i] += extraDerivative[i];
                }

                return entropyDerivative;
            }
            default:
                throw new UnsupportedOperationException("Unknown target type.");
        }
    }

    public double computeObjective(final FitPoint point_, final int endBlock_)
    {
        switch (_target)
        {
            case ENTROPY:
            {
                point_.computeUntil(endBlock_, BlockCalculationType.VALUE);
                final BlockResult aggregated = point_.getAggregated(BlockCalculationType.VALUE);
                return aggregated.getEntropyMean();
            }
            case TIC:
            {
                // TODO: This is extremely inefficient.....
                point_.computeUntil(endBlock_, BlockCalculationType.SECOND_DERIVATIVE);
                final BlockResult secondDerivative = point_.getAggregated(BlockCalculationType.SECOND_DERIVATIVE);

                final double entropy = secondDerivative.getEntropyMean();
                final double entropyStdDev = secondDerivative.getEntropyMeanDev();
                final double[] _gradient = secondDerivative.getDerivative();

                final RealMatrix jMatrix = secondDerivative.getSecondDerivative();
                final RealMatrix iMatrix = secondDerivative.getFisherInformation();

                final SingularValueDecomposition jSvd = new SingularValueDecomposition(jMatrix);
                final RealMatrix jInverse = jSvd.getSolver().getInverse();

                final RealMatrix ticMatrix = jInverse.multiply(iMatrix);

                double ticSum = 0.0;

                for (int i = 0; i < ticMatrix.getRowDimension(); i++)
                {
                    final double ticTerm = ticMatrix.getEntry(i, i);
                    ticSum += ticTerm;
                }

                final double tic = ticSum / point_.getSize();
                return entropy + tic;
            }
            case ICE:
            case ICE2:
            {
                point_.computeUntil(endBlock_, BlockCalculationType.FIRST_DERIVATIVE);
                final BlockResult secondDerivative = point_.getAggregated(BlockCalculationType.FIRST_DERIVATIVE);

                final double entropy = secondDerivative.getEntropyMean();
                final double entropyStdDev = secondDerivative.getEntropyMeanDev();
                final double[] _gradient = secondDerivative.getDerivative();

                // This is basically the worst an entropy could ever be with a uniform model. It is a reasonable
                // level of
                // "an entropy bad enough that any realistic model should avoid it like the plague, but not so bad that
                // it causes any sort of numerical issues", telling the model that J must be pos. def.

                // TODO: Fix this, we have no way to get this number here, so hard coding it.
                final double logM = Math.log(3) * point_.getSize();
                //final double iceBalance = 1.0 / (logM + _params.getEffectiveParamCount());
                final double iceBalance = 1.0 / logM;

                double iTermMax = 0.0;

                for (int i = 0; i < secondDerivative.getDerivativeDimension(); i++)
                {
                    iTermMax = Math.max(iTermMax, secondDerivative.getD2Entry(i));
                }

                if (iTermMax == 0.0)
                {
                    return entropy;
                }

                final double iTermCutoff = iTermMax * EPSILON;

                double iceSum = 0.0;
                double iceSum2 = 0.0;

                for (int i = 0; i < secondDerivative.getDerivativeDimension(); i++)
                {
                    final double iTerm = secondDerivative.getD2Entry(i); // Already squared, this one is.

                    if (iTerm < iTermCutoff)
                    {
                        // This particular term is irrelevant, its gradient is basically zero so just skip it.
                        continue;
                    }

                    final double jTerm = secondDerivative.getJDiagEntry(i);
                    final double iceTerm = iTerm / jTerm;

                    final double iceTerm2 = iTerm / (Math.max(jTerm, 0) * (1.0 - iceBalance) + iTerm * iceBalance);

                    iceSum += iceTerm;
                    iceSum2 += iceTerm2;
                }

                if (_target == OptimizationTarget.ICE2)
                {
                    final double iceAdjustment = iceSum2 / point_.getSize();
                    return entropy + iceAdjustment;
                }
                else
                {
                    final double iceAdjustment = iceSum / point_.getSize();
                    return entropy + iceAdjustment;
                }
            }
            default:
                throw new UnsupportedOperationException("Unknown target type.");
        }
    }

    public double computeObjectiveStdDev(final FitPoint point_, final int endBlock_)
    {
        point_.computeUntil(endBlock_, BlockCalculationType.VALUE);
        return point_.getAggregated(BlockCalculationType.VALUE).getEntropyMeanDev();
    }


    /**
     * How many sigma higher/lower than other_ is this block result. If positive, this is X sigma higher than other_.
     * <p>
     * N.B: This is calculated based on the differences. Merely having a high std. dev. will not make much
     * difference, it's about how consistently one-sided the differences are.
     * <p>
     * continue calculating until data runs out or the given stdDev has been hit.
     *
     * @param a_
     * @param b_
     * @return
     */
    public double compare(final FitPoint a_, final FitPoint b_, final double minStdDev_)
    {
        if (a_ == b_)
        {
            // These are identical objects, the answer would always be zero.
            return 0.0;
        }
        if (a_.getBlockCount() != b_.getBlockCount())
        {
            throw new IllegalArgumentException("Incomparable points.");
        }

        if (a_.getNextBlock(BlockCalculationType.VALUE) > b_.getNextBlock(BlockCalculationType.VALUE))
        {
            return -1.0 * compare(b_, a_, minStdDev_);
        }

        final BlockCalculationType valType = BlockCalculationType.VALUE;

        // b_ has at least as many values as a_
        final VarianceCalculator vcalc = new VarianceCalculator();
        final double sqrtBlockSize = Math.sqrt(a_.getBlockSize());

        for (int i = 0; i < a_.getBlockCount(); i++)
        {
            if (i >= a_.getNextBlock(valType))
            {
                if (i >= _superBlockSize && vcalc.getMean() != 0.0)
                {
                    // Only look at the std. dev. if i is large enough, and we've seen at
                    // least a few differences so far.
                    final double dev = vcalc.getDev() * sqrtBlockSize;

                    if (Math.abs(dev) >= minStdDev_)
                    {
                        // We are done, no further calculations needed.
                        break;
                    }
                }

                final int target = Math.min(i + _superBlockSize, a_.getBlockCount());
                a_.computeUntil(target, valType);
                b_.computeUntil(target, valType);
                // OK, carry on now that data has been computed.
            }

            final BlockResult a = a_.getBlock(i, valType);
            final BlockResult b = b_.getBlock(i, valType);

            if (a.getRowStart() != b.getRowStart())
            {
                throw new IllegalArgumentException("Misaligned blocks.");
            }
            if (a.getRowEnd() != b.getRowEnd())
            {
                throw new IllegalArgumentException("Misaligned blocks.");
            }

            final double aMean = a.getEntropyMean();
            final double bMean = b.getEntropyMean();
            final double diff = (aMean - bMean);
            vcalc.update(diff);
        }

        if (a_.getNextBlock(valType) < 1)
        {
            // No data, can't tell which is better.
            return 0.0;
        }

        final double meanDiff = vcalc.getMean();
        double dev = vcalc.getMeanDev();

        if (vcalc.getCount() < 3 || 0.0 == dev)
        {
            // Special case, can't do much else about the variance.
            // This is a considerable overestimate.
            final double devA = a_.getBlock(0, valType).getEntropyMeanDev();
            final double devB = b_.getBlock(0, valType).getEntropyMeanDev();
            dev = 0.5 * (devA + devB);
        }

        // This is slightly rephrased in order to make later computations much easier.
        final int nextBlock = Math.max(a_.getNextBlock(BlockCalculationType.VALUE),
                b_.getNextBlock(BlockCalculationType.VALUE));
        final double zScore = (computeObjective(a_, nextBlock) - computeObjective(b_, nextBlock)) / dev;


        //final double zScore2 = meanDiff / dev;
        return zScore;
    }


}
