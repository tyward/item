package edu.columbia.tjw.item.fit.calculator;

import edu.columbia.tjw.item.ItemSettings;
import edu.columbia.tjw.item.algo.DoubleVector;
import edu.columbia.tjw.item.algo.VarianceCalculator;
import edu.columbia.tjw.item.algo.VectorTools;
import edu.columbia.tjw.item.optimize.OptimizationTarget;
import edu.columbia.tjw.item.util.IceTools;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;

public final class FitPointAnalyzer
{
    private static final double EPSILON = Math.ulp(4.0); // Just a bit bigger than machine epsilon.

    private static final double Z_SCORE_CUTOFF = 5.0;

    private final int _superBlockSize;
    private final double _minStdDev;
    private final OptimizationTarget _target;
    private final ItemSettings _settings;

    public FitPointAnalyzer(final int superBlockSize_, final OptimizationTarget target_,
                            ItemSettings settings_)
    {
        _superBlockSize = superBlockSize_;
        _minStdDev = Z_SCORE_CUTOFF;
        _target = target_;
        _settings = settings_;
    }

    public double compare(final FitPoint a_, final FitPoint b_)
    {
        return generateComparision(a_, b_).getZScore();
    }

    public FitPointComparison generateComparision(final FitPoint a_, final FitPoint b_)
    {
        return compare(a_, b_, _minStdDev, false);
    }


    public double getSigmaTarget()
    {
        return _minStdDev;
    }

    public DoubleVector getDerivativeAdjustment(final FitPoint point_, final FitPoint prev_)
    {
        switch (_target)
        {
            case ENTROPY:
            {
                // No adjustment needed.
//                final double[] output = new double[point_.getDimension()];
//                return output;
                return DoubleVector.constantVector(0.0, point_.getDimension());
            }
            case L2:
            {
                final double lambda = _settings.getL2Lambda();
                final DoubleVector params = point_.getParameters();
                return VectorTools.scalarMultiply(params, 2.0 * lambda);
            }
            case ICE_SIMPLE:
            case ICE2:
            {
                point_.computeAll(BlockCalculationType.SECOND_DERIVATIVE);
                final BlockResult aggregated = point_.getAggregated(BlockCalculationType.SECOND_DERIVATIVE);
                final DoubleVector extraDerivative = IceTools.fillIceExtraDerivative(aggregated);
                return extraDerivative;
            }
            case ICE_STABLE_B:
            {
                point_.computeAll(BlockCalculationType.SECOND_DERIVATIVE);
                final BlockResult aggregated = point_.getAggregated(BlockCalculationType.SECOND_DERIVATIVE);
                final DoubleVector extraDerivative = IceTools.fillIceStableBExtraDerivative(aggregated);
                return extraDerivative;
            }
            case ICE_RAW:
            {
                // There is no realistic way to compute this efficiently, just fall through and use the ICE
                // derivatives instead.
            }
            case ICE:
            case ICE_B:
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
                point_.computeAll(BlockCalculationType.FIRST_DERIVATIVE, jAgg);
                final BlockResult aggregated = point_.getAggregated(BlockCalculationType.FIRST_DERIVATIVE);

                final double[] extraDerivative4;

                if (_target == OptimizationTarget.ICE_B)
                {
                    // ICE5.
                    extraDerivative4 = aggregated.getScaledGradient2();
                }
                else
                {
                    extraDerivative4 = aggregated.getScaledGradient();
                }

                for (int i = 0; i < extraDerivative4.length; i++)
                {
                    extraDerivative4[i] /= point_.getSize();
                }

                return DoubleVector.of(extraDerivative4, false);
            }
            default:
                throw new UnsupportedOperationException("Unknown target type.");
        }
    }


    public DoubleVector getDerivative(final FitPoint point_)
    {
        return getDerivative(point_, null);
    }

    public DoubleVector getDerivative(final FitPoint point_, final FitPoint prev_)
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
                final DoubleVector entropyDerivative = aggregated.getDerivative();
                final double lambda = _settings.getL2Lambda();
                final DoubleVector params = point_.getParameters();
                final DoubleVector scaled = VectorTools.scalarMultiply(params, 2.0 * lambda);
                return VectorTools.add(entropyDerivative, scaled);
            }
            case ICE_SIMPLE:
            case ICE2:
            {
                point_.computeAll(BlockCalculationType.SECOND_DERIVATIVE);
                final BlockResult aggregated = point_.getAggregated(BlockCalculationType.SECOND_DERIVATIVE);
                final DoubleVector entropyDerivative = aggregated.getDerivative();
                final DoubleVector extraDerivative = this.getDerivativeAdjustment(point_, prev_);

                return VectorTools.add(entropyDerivative, extraDerivative);
            }
            case ICE_STABLE_B:
            {
                point_.computeAll(BlockCalculationType.SECOND_DERIVATIVE);
                final BlockResult aggregated = point_.getAggregated(BlockCalculationType.SECOND_DERIVATIVE);

                final int dimension = aggregated.getDerivativeDimension();
                final DoubleVector entropyDerivative = aggregated.getDerivative();
                final DoubleVector extraDerivative = this.getDerivativeAdjustment(point_, prev_);
                return VectorTools.add(entropyDerivative, extraDerivative);
            }
            case ICE_RAW:
            {
                // There is no realistic way to compute this efficiently, just fall through and use the ICE
                // derivatives instead.
            }
            case ICE:
            case ICE_B:
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
                point_.computeAll(BlockCalculationType.FIRST_DERIVATIVE, jAgg);
                final BlockResult aggregated = point_.getAggregated(BlockCalculationType.FIRST_DERIVATIVE);

                final int dimension = aggregated.getDerivativeDimension();
                final DoubleVector entropyDerivative = aggregated.getDerivative();
                final DoubleVector extraDerivative = this.getDerivativeAdjustment(point_, prev_);
                return VectorTools.add(entropyDerivative, extraDerivative);
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

                final DoubleVector params = point_.getParameters();
                final double dot = VectorTools.dot(params, params);
                return entropy + lambda * dot;
            }
            case ICE_RAW:
            {
                // TODO: This is extremely inefficient.....
                point_.computeUntil(endBlock_, BlockCalculationType.SECOND_DERIVATIVE);
                final BlockResult secondDerivative = point_.getAggregated(BlockCalculationType.SECOND_DERIVATIVE);

                final double entropy = secondDerivative.getEntropyMean();
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
            case ICE_SIMPLE:
            case ICE2:
            case ICE_STABLE_B:
            case ICE:
            case ICE_B:
            {
                point_.computeUntil(endBlock_, BlockCalculationType.FIRST_DERIVATIVE);
                final BlockResult secondDerivative = point_.getAggregated(BlockCalculationType.FIRST_DERIVATIVE);

                final double entropy = secondDerivative.getEntropyMean();

                if (_target == OptimizationTarget.ICE_SIMPLE)
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
    public FitPointComparison compare(final FitPoint a_, final FitPoint b_, final double minStdDev_,
                                      final boolean reverse_)
    {
//        if (a_ == b_)
//        {
//            // These are identical objects, the answer would always be zero.
//            return 0.0;
//        }
        if (a_.getBlockCount() != b_.getBlockCount())
        {
            throw new IllegalArgumentException("Incomparable points.");
        }

        if (a_.getNextBlock(BlockCalculationType.VALUE) > b_.getNextBlock(BlockCalculationType.VALUE))
        {
            return compare(b_, a_, minStdDev_, !reverse_);
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
            return new FitPointComparison(a_, b_, 0, 0.0);
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
        //final double zScore = (computeObjective(a_, nextBlock) - computeObjective(b_, nextBlock)) / dev;

        final FitPointComparison comparison;

        if (reverse_)
        {
            comparison = new FitPointComparison(b_, a_, nextBlock, dev);
        }
        else
        {
            comparison = new FitPointComparison(a_, b_, nextBlock, dev);
        }

        return comparison;
    }

    public final class FitPointComparison
    {
        private final FitPoint _pointA;
        private final FitPoint _pointB;
        private final int _blockCount;

        private final double _aVal;
        private final double _bVal;
        private final double _dev;
        private final double _zScore;
        private final double _relativeError;

        public FitPointComparison(final FitPoint pointA_, final FitPoint pointB_, final int nextBlock_,
                                  final double dev_)
        {
            _pointA = pointA_;
            _pointB = pointB_;
            _blockCount = nextBlock_;

            if (_blockCount < 1)
            {
                _aVal = 0.0;
                _bVal = 0.0;
                _dev = 0.0;
                _zScore = 0.0;
                _relativeError = 0.0;
            }
            else
            {
                _aVal = computeObjective(_pointA, _blockCount);
                _bVal = computeObjective(_pointB, _blockCount);
                _dev = dev_;

                _zScore = (_aVal - _bVal) / _dev;
                _relativeError = Math.abs(_aVal - _bVal) / (_aVal + _bVal);
            }
        }

        public FitPoint getPointA()
        {
            return _pointA;
        }

        public FitPoint getPointB()
        {
            return _pointB;
        }

        public int getBlockCount()
        {
            return _blockCount;
        }

        public double getValA()
        {
            return _aVal;
        }

        public double getValB()
        {
            return _bVal;
        }

        public double getDev()
        {
            return _dev;
        }

        public double getZScore()
        {
            return _zScore;
        }

        public double getRelativeError()
        {
            return _relativeError;
        }
    }

}
