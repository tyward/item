package edu.columbia.tjw.item.fit.calculator;

import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemModel;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.algo.DoubleVector;
import edu.columbia.tjw.item.algo.VectorTools;
import edu.columbia.tjw.item.data.ItemFittingGrid;
import edu.columbia.tjw.item.fit.ParamFittingGrid;
import edu.columbia.tjw.item.util.IceTools;
import edu.columbia.tjw.item.util.MathTools;

public final class BlockResultCalculator<S extends ItemStatus<S>, R extends ItemRegressor<R>,
        T extends ItemCurveType<T>>
{
    private final ItemFittingGrid<S, R> _grid;
    private final int _rowOffset;

    public BlockResultCalculator(final ItemFittingGrid<S, R> grid_)
    {
        this(grid_, 0);
    }

    public BlockResultCalculator(final ItemFittingGrid<S, R> grid_, final int rowOffset_)
    {
        if (null == grid_)
        {
            throw new NullPointerException("Grid cannot be null.");
        }
        if (rowOffset_ < 0)
        {
            throw new IllegalArgumentException("Row offset must be nonnegative: " + rowOffset_);
        }

        _grid = grid_;
        _rowOffset = rowOffset_;
    }

    public ItemFittingGrid<S, R> getGrid()
    {
        return _grid;
    }

    public synchronized BlockResult compute(final ItemModel<S, R, T> model_,
                                            final BlockCalculationType type_, final BlockResult derivativeBlock_)
    {
        if (!model_.getParams().getStatus().equals(_grid.getFromStatus()))
        {
            throw new IllegalArgumentException("Status mismatch.");
        }

        final ParamFittingGrid<S, R, T> grid = new ParamFittingGrid<>(model_.getParams(), _grid);

        double entropySum = 0.0;
        double x2 = 0.0;
        final int count = grid.size();

        if (count <= 0)
        {
            throw new IllegalArgumentException("Grid must have positive size.");
        }

        for (int i = 0; i < grid.size(); i++)
        {
            final double entropy = model_.logLikelihood(grid, i);
            final double e2 = entropy * entropy;
            entropySum += entropy;
            x2 += e2;
        }

        final DoubleVector.Builder derivative;
        final DoubleVector.Builder d2;
        final DoubleVector.Builder jDiag;
        final DoubleVector.Builder shiftGradient;
        final double[][] secondDerivative;
        final double[][] fisherInformation;
        final DoubleVector.Builder scaledGradient;
        final DoubleVector.Builder scaledGradient2;
        final DoubleVector prevJDiag;
        final DoubleVector prevJWeight;
        final double prevDiagCutoff;
        double gradientMass = 0.0;

        if (null != derivativeBlock_)
        {
            prevJDiag = derivativeBlock_.getJDiag();
            prevJWeight = IceTools.computeJWeight(prevJDiag);

            prevDiagCutoff = MathTools.SQRT_EPSILON * VectorTools.maxAbsElement(prevJDiag);
        }
        else
        {
            prevJDiag = null;
            prevJWeight = null;
            prevDiagCutoff = Double.NaN;
        }

        if (type_ == BlockCalculationType.SECOND_DERIVATIVE)
        {
            final int dimension = model_.getDerivativeSize();
            //derivative = new double[dimension];
            derivative = DoubleVector.newBuilder(dimension);
            d2 = DoubleVector.newBuilder(dimension);
            jDiag = DoubleVector.newBuilder(dimension);
            scaledGradient = DoubleVector.newBuilder(dimension);
            scaledGradient2 = DoubleVector.newBuilder(dimension);
            shiftGradient = DoubleVector.newBuilder(dimension);
            fisherInformation = new double[dimension][dimension];
            secondDerivative = new double[dimension][dimension];
        }
        else if (type_ == BlockCalculationType.FIRST_DERIVATIVE)
        {
            final int dimension = model_.getDerivativeSize();
            derivative = DoubleVector.newBuilder(dimension);
            d2 = DoubleVector.newBuilder(dimension);
            jDiag = DoubleVector.newBuilder(dimension);
            scaledGradient = DoubleVector.newBuilder(dimension);
            scaledGradient2 = DoubleVector.newBuilder(dimension);
            fisherInformation = null;
            secondDerivative = null;
            shiftGradient = null;
        }
        else
        {
            derivative = null;
            d2 = null;
            jDiag = null;
            fisherInformation = null;
            secondDerivative = null;
            shiftGradient = null;
            scaledGradient = null;
            scaledGradient2 = null;
        }

        if (derivative != null)
        {
            final int dimension = derivative.getSize();
            final double[] tmp = new double[dimension];

            final double[] diagTmp = new double[dimension];
            final double[][] tmp2;

            if (secondDerivative != null)
            {
                tmp2 = new double[dimension][dimension];
            }
            else
            {
                tmp2 = null;
            }

            for (int i = 0; i < count; i++)
            {
                model_.computeGradient(grid, i, tmp, diagTmp, tmp2);

                if (tmp2 != null)
                {
                    for (int k = 0; k < dimension; k++)
                    {
                        double shiftSum = 0.0;

                        for (int w = 0; w < dimension; w++)
                        {
                            shiftSum += tmp[w] * tmp2[k][w];
                        }

                        shiftGradient.addToEntry(k, shiftSum);
                    }
                }

                double gradientScale;

                if (null != prevJDiag)
                {
                    gradientScale = IceTools.computeIce3Sum(tmp, prevJDiag, prevJWeight, false);
                }
                else
                {
                    gradientScale = Double.NaN;
                }

                gradientMass += gradientScale;
                derivative.add(tmp);
                jDiag.add(diagTmp);

                for (int k = 0; k < dimension; k++)
                {
                    d2.addToEntry(k, tmp[k] * tmp[k]);

                    if (!Double.isNaN(gradientScale))
                    {
                        scaledGradient.addToEntry(k, 2.0 * tmp[k] * gradientScale);

                        final double elemScale = diagTmp[k] / Math.max(prevDiagCutoff, prevJDiag.getEntry(k));

                        scaledGradient2.addToEntry(k, 2.0 * tmp[k] * elemScale);
                    }

                    if (secondDerivative != null)
                    {
                        for (int w = 0; w < dimension; w++)
                        {
                            fisherInformation[k][w] += tmp[k] * tmp[w];
                            secondDerivative[k][w] += tmp2[k][w];
                        }
                    }
                }
            }

            if (count > 0)
            {
                //N.B: we are computing the negative log likelihood.
                final double invCount = 1.0 / count;
                derivative.scalarMultiply(invCount);
                d2.scalarMultiply(invCount);
                scaledGradient.scalarMultiply(invCount);
                scaledGradient2.scalarMultiply(invCount);
                jDiag.scalarMultiply(invCount);

                if (shiftGradient != null)
                {
                    shiftGradient.scalarMultiply(invCount);
                }

                for (int i = 0; i < dimension; i++)
                {
                    if (secondDerivative != null)
                    {
                        for (int w = 0; w < dimension; w++)
                        {
                            fisherInformation[i][w] *= invCount;
                            secondDerivative[i][w] *= invCount;
                        }
                    }
                }
            }
        }


        return new BlockResult(_rowOffset, _rowOffset + count, entropySum, x2,
                DoubleVector.of(derivative), DoubleVector.of(d2), DoubleVector.of(jDiag),
                DoubleVector.of(shiftGradient), DoubleVector.of(scaledGradient),
                DoubleVector.of(scaledGradient2),
                gradientMass,
                fisherInformation,
                secondDerivative);
    }
}

