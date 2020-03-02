package edu.columbia.tjw.item.fit.calculator;

import edu.columbia.tjw.item.ItemSettings;
import edu.columbia.tjw.item.algo.VarianceCalculator;
import edu.columbia.tjw.item.optimize.OptimizationTarget;
import edu.columbia.tjw.item.util.IceTools;
import edu.columbia.tjw.item.util.MathTools;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;

public final class FitPointAnalyzer
{
    private static final double EPSILON = Math.ulp(4.0); // Just a bit bigger than machine epsilon.

    private final int _superBlockSize;
    private final double _minStdDev;
    private final OptimizationTarget _target;
    private final ItemSettings _settings;

    public FitPointAnalyzer(final int superBlockSize_, final double minStdDev_, final OptimizationTarget target_,
                            ItemSettings settings_)
    {
        _superBlockSize = superBlockSize_;
        _minStdDev = minStdDev_;
        _target = target_;
        _settings = settings_;
    }

    public double compare(final FitPoint a_, final FitPoint b_)
    {
        return compare(a_, b_, _minStdDev);
    }

    public double getSigmaTarget()
    {
        return _minStdDev;
    }

    public double[] getDerivativeAdjustment(final FitPoint point_, final FitPoint prev_)
    {
        switch (_target)
        {
            case ENTROPY:
            {
                // No adjustment needed.
                final double[] output = new double[point_.getDimension()];
                return output;
            }
            case L2:
            {
                final double lambda = _settings.getL2Lambda();
                final double[] params = point_.getParameters();

                MathTools.scalarMultiply(2.0 * lambda, params);

                return params;
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
                final double[] extraDerivative = IceTools.fillIceExtraDerivative(aggregated);
                return extraDerivative;
            }
            case ICE3:
            {
                point_.computeAll(BlockCalculationType.SECOND_DERIVATIVE);
                final BlockResult aggregated = point_.getAggregated(BlockCalculationType.SECOND_DERIVATIVE);
                final double[] extraDerivative = IceTools.fillIce3ExtraDerivative(aggregated);
                return extraDerivative;
            }
            case ICE4:
            case ICE5:
            {
                // Unfortunately, we have to clear here.
                point_.clear();
                final FitPoint jPoint;

                if (prev_ != null)
                {
                    jPoint = prev_;
                }
                else
                {
                    jPoint = point_;
                }

                jPoint.computeAll(BlockCalculationType.FIRST_DERIVATIVE);
                final BlockResult jAgg = jPoint.getAggregated(BlockCalculationType.FIRST_DERIVATIVE);

                // Downgrade this to first derivative once the testing is done.
                point_.computeAll(BlockCalculationType.SECOND_DERIVATIVE, jAgg);
                final BlockResult aggregated = point_.getAggregated(BlockCalculationType.SECOND_DERIVATIVE);

                final int dimension = aggregated.getDerivativeDimension();
                final double[] extraDerivative = IceTools.fillIce3ExtraDerivative(aggregated);
                final double[] extraDerivative4;

                if (_target == OptimizationTarget.ICE4)
                {
                    extraDerivative4 = aggregated.getScaledGradient();
                }
                else
                {
                    // ICE5.
                    extraDerivative4 = aggregated.getScaledGradient2();
                }

                for (int i = 0; i < extraDerivative4.length; i++)
                {
                    extraDerivative4[i] /= point_.getSize();
                }

                return extraDerivative4;
            }
            default:
                throw new UnsupportedOperationException("Unknown target type.");
        }
    }


    public double[] getDerivative(final FitPoint point_)
    {
        return getDerivative(point_, null);
    }

    public double[] getDerivative(final FitPoint point_, final FitPoint prev_)
    {
        switch (_target)
        {
            case ENTROPY:
            {
                point_.computeAll(BlockCalculationType.FIRST_DERIVATIVE);
                final BlockResult aggregated = point_.getAggregated(BlockCalculationType.FIRST_DERIVATIVE);
                return aggregated.getDerivative();
            }
            case L2:
            {
                point_.computeAll(BlockCalculationType.FIRST_DERIVATIVE);
                final BlockResult aggregated = point_.getAggregated(BlockCalculationType.FIRST_DERIVATIVE);
                final double[] entropyDerivative = aggregated.getDerivative();
                final double lambda = _settings.getL2Lambda();
                final double[] params = point_.getParameters();

                MathTools.scalarMultiply(2.0 * lambda, params);

                for (int i = 0; i < params.length; i++)
                {
                    entropyDerivative[i] += params[i];
                }

                return entropyDerivative;
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
                final double[] extraDerivative = this.getDerivativeAdjustment(point_, prev_);

                for (int i = 0; i < dimension; i++)
                {
                    entropyDerivative[i] += extraDerivative[i];
                }

                return entropyDerivative;
            }
            case ICE3:
            {
                point_.computeAll(BlockCalculationType.SECOND_DERIVATIVE);
                final BlockResult aggregated = point_.getAggregated(BlockCalculationType.SECOND_DERIVATIVE);

                final int dimension = aggregated.getDerivativeDimension();
                final double[] entropyDerivative = aggregated.getDerivative();
                final double[] extraDerivative = this.getDerivativeAdjustment(point_, prev_);
                final double[] edClone = entropyDerivative.clone();

                for (int i = 0; i < dimension; i++)
                {
                    entropyDerivative[i] += extraDerivative[i];
                }

//                System.out.println(
//                        "Combined cos similarity[" + MathTools.magnitude(entropyDerivative) + "]: " + MathTools
//                                .cos(edClone, entropyDerivative));

                return entropyDerivative;
            }
            case ICE4:
            case ICE5:
            {
                // Unfortunately, we have to clear here.
                point_.clear();
                final FitPoint jPoint;

                if (prev_ != null)
                {
                    jPoint = prev_;
                }
                else
                {
                    jPoint = point_;
                }

                jPoint.computeAll(BlockCalculationType.FIRST_DERIVATIVE);
                final BlockResult jAgg = jPoint.getAggregated(BlockCalculationType.FIRST_DERIVATIVE);

                // Downgrade this to first derivative once the testing is done.
                point_.computeAll(BlockCalculationType.SECOND_DERIVATIVE, jAgg);
                final BlockResult aggregated = point_.getAggregated(BlockCalculationType.SECOND_DERIVATIVE);

                final int dimension = aggregated.getDerivativeDimension();
                final double[] entropyDerivative = aggregated.getDerivative();
                final double[] extraDerivative = this.getDerivativeAdjustment(point_, prev_);

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
            case L2:
            {
                point_.computeUntil(endBlock_, BlockCalculationType.VALUE);
                final BlockResult aggregated = point_.getAggregated(BlockCalculationType.VALUE);
                final double entropy = aggregated.getEntropyMean();

                final double lambda = _settings.getL2Lambda();

                final double[] params = point_.getParameters();
                final double dot = MathTools.dot(params, params);
                return entropy + lambda * dot;
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
            case ICE3:
            case ICE4:
            case ICE5:
            {
                point_.computeUntil(endBlock_, BlockCalculationType.FIRST_DERIVATIVE);
                final BlockResult secondDerivative = point_.getAggregated(BlockCalculationType.FIRST_DERIVATIVE);

                final double entropy = secondDerivative.getEntropyMean();

                if (_target == OptimizationTarget.ICE)
                {
                    final double iceSum = IceTools.computeIceSum(secondDerivative);
                    final double iceAdjustment = iceSum / point_.getSize();
                    return entropy + iceAdjustment;
                }
                else if (_target == OptimizationTarget.ICE2)
                {
                    final double iceSum2 = IceTools.computeIce2Sum(secondDerivative);
                    final double iceAdjustment = iceSum2 / point_.getSize();
                    return entropy + iceAdjustment;
                }
                else
                {
                    final double iceSum3 = IceTools.computeIce3Sum(secondDerivative);
                    final double iceAdjustment = iceSum3 / point_.getSize();
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
