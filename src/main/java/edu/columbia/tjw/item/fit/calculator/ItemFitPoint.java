package edu.columbia.tjw.item.fit.calculator;

import edu.columbia.tjw.item.*;
import edu.columbia.tjw.item.fit.PackedParameters;
import edu.columbia.tjw.item.util.thread.GeneralTask;
import edu.columbia.tjw.item.util.thread.GeneralThreadPool;

import java.util.ArrayList;
import java.util.List;

public final class ItemFitPoint<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
        implements FitPoint
{
    private static final GeneralThreadPool POOL = GeneralThreadPool.singleton();

    private final List<BlockResultCalculator<S, R, T>> _blockCalculators;
    private final ItemModel<S, R, T> _model;
    private final int _blockSize;
    private final int _totalSize;

    private final BlockResultCompound[] _compound;
    private int[] _nextBlock;

    public ItemFitPoint(final FitPointGenerator<S, R, T> calculator_, final PackedParameters<S, R, T> packed_)
    {
        if (null == calculator_)
        {
            throw new NullPointerException("Calculator cannot be null.");
        }
        if (null == packed_)
        {
            throw new NullPointerException("Packed cannot be null.");
        }

        _blockCalculators = calculator_.getCalculators();
        _model = new ItemModel<>(packed_);
        _blockSize = calculator_.getBlockSize();
        _totalSize = calculator_.getRowCount();

        _nextBlock = new int[BlockCalculationType.getValueCount()];
        _compound = new BlockResultCompound[BlockCalculationType.getValueCount()];

        for (int i = 0; i < _compound.length; i++)
        {
            _compound[i] = new BlockResultCompound();
        }

    }

    public ItemParameters<S, R, T> getParams()
    {
        return _model.getParams();
    }

    @Override
    public int getBlockSize()
    {
        return _blockSize;
    }

    @Override
    public int getBlockCount()
    {
        return _blockCalculators.size();
    }

    @Override
    public int getNextBlock(BlockCalculationType type_)
    {
        return _nextBlock[type_.ordinal()];
    }

    @Override
    public void computeAll(BlockCalculationType type_)
    {
        computeUntil(getBlockCount(), type_);
    }

    @Override
    public BlockResult getAggregated(BlockCalculationType type_)
    {
        return _compound[type_.ordinal()].getAggregated();
    }

    @Override
    public void computeUntil(final int endBlock_, BlockCalculationType type_)
    {
        final int nextBlock = getNextBlock(type_);
        final int neededBlocks = endBlock_ - nextBlock;

        if (neededBlocks <= 0)
        {
            return;
        }

        final List<EntropyRunner> runners = new ArrayList<>(neededBlocks);

        for (int i = nextBlock; i < endBlock_; i++)
        {
            final BlockResultCalculator<S, R, T> calc = _blockCalculators.get(i);
            final EntropyRunner runner = new EntropyRunner(calc, type_);
            runners.add(runner);
        }

        final List<BlockResult> analysis = POOL.runAll(runners);
        final BlockResultCompound target = _compound[type_.ordinal()];

        for (final BlockResult result : analysis)
        {
            final int blockIndex = target.getBlockCount();

            for (int k = 0; k < type_.ordinal(); k++)
            {
                if (_compound[k].getBlockCount() > target.getBlockCount())
                {
                    // The new block replaces the previous one, as it has more information.
                    if (_compound[k].getBlock(blockIndex).getEntropyMean() != result.getEntropyMean())
                    {
                        // This should not be possible. We computed an exact replacement block with more information,
                        // and the info in common doesn't match exactly.
                        throw new IllegalStateException("Block mismatch!");
                    }

                    _compound[k].setResult(blockIndex, result);
                }
                else
                {
                    _compound[k].appendResult(result);
                }
            }

            target.appendResult(result);
        }

        // Computing at one level implies computation at all lower levels. For instance, a gradient
        // implies a value as well.
        for (int i = 0; i <= type_.ordinal(); i++)
        {
            _nextBlock[i] = endBlock_;
        }
    }

    @Override
    public BlockResult getBlock(final int index_, BlockCalculationType type_)
    {
        return _compound[type_.ordinal()].getBlock(index_);
    }

//    @Override
//    public double getObjective(int boundary_)
//    {
//        if (boundary_ == 0)
//        {
//            return 0.0;
//        }
//
//
////        if (!USE_ICE)
////        {
//        this.computeUntil(boundary_, BlockCalculationType.VALUE);
//        return this.getAggregated(BlockCalculationType.VALUE).getEntropyMean();
////        }
////
////        // TODO: This is extremely inefficient.....
////        this.computeUntil(boundary_, BlockCalculationType.SECOND_DERIVATIVE);
////        final BlockResult secondDerivative = this.getAggregated(BlockCalculationType.SECOND_DERIVATIVE);
////
////        final double entropy = secondDerivative.getEntropyMean();
////        final double entropyStdDev = secondDerivative.getEntropyMeanDev();
////        final double[] _gradient = secondDerivative.getDerivative();
////
////        final RealMatrix jMatrix = secondDerivative.getSecondDerivative();
////
////
////        final RealMatrix iMatrix = secondDerivative.getFisherInformation();
////
////        if (USE_TIC)
////        {
////            final SingularValueDecomposition iSvd = new SingularValueDecomposition(iMatrix);
////            final SingularValueDecomposition jSvd = new SingularValueDecomposition(jMatrix);
////            final RealMatrix jInverse = jSvd.getSolver().getInverse();
////            final RealMatrix iInverse = iSvd.getSolver().getInverse();
////
////            final RealMatrix ticMatrix = jInverse.multiply(iMatrix);
////
////            double ticSum = 0.0;
////
////            for (int i = 0; i < ticMatrix.getRowDimension(); i++)
////            {
////                final double ticTerm = ticMatrix.getEntry(i, i);
////                ticSum += ticTerm;
////            }
////
////            final double tic = ticSum / this.getSize();
////            return entropy + tic;
////        }
////        else
////        {
////            // This is basically the worst an entropy could ever be with a uniform model. It is a reasonable
////            // level of
////            // "an entropy bad enough that any realistic model should avoid it like the plague, but not so bad that
////            // it causes any sort of numerical issues", telling the model that J must be pos. def.
////            final double logM = Math.log(_model.getParams().getReachableSize());
////            //final double iceBalance = 1.0 / (logM + _params.getEffectiveParamCount());
////            final double iceBalance = 1.0 / logM;
////
////            double iceSum = 0.0;
////            double iceSum2 = 0.0;
////
////            for (int i = 0; i < iMatrix.getRowDimension(); i++)
////            {
////                final double iTerm = iMatrix.getEntry(i, i); // Already squared, this one is.
////                final double jTerm = jMatrix.getEntry(i, i);
////                final double iceTerm = iTerm / jTerm;
////
////                final double iceTerm2 = iTerm / (Math.abs(jTerm) * (1.0 - iceBalance) + iTerm * iceBalance);
////
////                iceSum += iceTerm;
////                iceSum2 += iceTerm2;
////            }
////
////            final double iceAdjustment = iceSum2 / this.getSize();
////            return entropy + iceAdjustment;
////        }
//    }
//
//    @Override
//    public double getObjectiveStdDev(int boundary_)
//    {
//        // TODO: Is this right, shouldn't we make sure the counts line up first?
//        if (boundary_ == 0)
//        {
//            return 0.0;
//        }
//
//        // To a first approximation, the std. dev. is about right, so just leave it.
////        if (!USE_ICE)
////        {
//        this.computeUntil(boundary_, BlockCalculationType.VALUE);
//        return this.getAggregated(BlockCalculationType.VALUE).getEntropyMeanDev();
////        }
////        else
////        {
////
////        }
//    }

    public int getSize()
    {
        return this._totalSize;
    }


    private final class EntropyRunner extends GeneralTask<BlockResult>
    {
        private final BlockResultCalculator<S, R, T> _calc;
        private final BlockCalculationType _type;

        public EntropyRunner(final BlockResultCalculator<S, R, T> calc_,
                             final BlockCalculationType type_)
        {
            _calc = calc_;
            _type = type_;
        }

        @Override
        protected BlockResult subRun() throws Exception
        {
            // N.B: we clone the model since ItemModel isn't threadsafe (it has internal state).
            // However, cloning models is a bit faster than making new ones because of the internal (immutable)
            // parameters.
            return _calc.compute(_model.clone(), _type);
        }
    }
}
