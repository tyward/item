package edu.columbia.tjw.item.fit.calculator;

import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemModel;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemStatus;
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

        final double[] derivative;
        final double[] d2;
        final double[] jDiag;
        final double[] shiftGradient;
        final double[][] secondDerivative;
        final double[][] fisherInformation;
        final double[] scaledGradient;
        final double[] scaledGradient2;
        final double[] prevJDiag;
        final double[] prevJWeight;
        final double prevDiagCutoff;
        double gradientMass = 0.0;

        if (null != derivativeBlock_)
        {
            prevJDiag = derivativeBlock_.getJDiag();
            prevJWeight = IceTools.computeJWeight(prevJDiag);

            prevDiagCutoff = MathTools.SQRT_EPSILON * MathTools.maxAbsElement(prevJDiag);
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
            derivative = new double[dimension];
            d2 = new double[dimension];
            jDiag = new double[dimension];
            scaledGradient = new double[dimension];
            scaledGradient2 = new double[dimension];
            shiftGradient = new double[dimension];
            fisherInformation = new double[dimension][dimension];
            secondDerivative = new double[dimension][dimension];
        }
        else if (type_ == BlockCalculationType.FIRST_DERIVATIVE)
        {
            final int dimension = model_.getDerivativeSize();
            derivative = new double[dimension];
            d2 = new double[dimension];
            jDiag = new double[dimension];
            scaledGradient = new double[dimension];
            scaledGradient2 = new double[dimension];
            fisherInformation = new double[dimension][dimension];
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
            final int dimension = derivative.length;
            final double[] tmp = new double[dimension];
            final double[] t2 = new double[dimension];

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

                        shiftGradient[k] += shiftSum;
                    }
                }

                double gradientScale;

                if (null != prevJDiag)
                {
                    for (int w = 0; w < dimension; w++)
                    {
                        t2[w] = tmp[w] * tmp[w];
                    }

                    gradientScale = IceTools.computeIce3Sum(t2, prevJDiag, prevJWeight);
                }
                else
                {
                    gradientScale = Double.NaN;
                }

                gradientMass += gradientScale;

                for (int k = 0; k < dimension; k++)
                {
                    derivative[k] += tmp[k];
                    d2[k] += tmp[k] * tmp[k];
                    jDiag[k] += diagTmp[k];

                    if (!Double.isNaN(gradientScale))
                    {
                        scaledGradient[k] += 2.0 * tmp[k] * gradientScale;

                        final double elemScale = diagTmp[k] / Math.max(prevDiagCutoff, prevJDiag[k]);

                        scaledGradient2[k] += 2.0 * tmp[k] * elemScale;
                    }


                    for (int w = 0; w < dimension; w++)
                    {
                        fisherInformation[k][w] += tmp[k] * tmp[w];
                    }

                    if (secondDerivative != null)
                    {
                        for (int w = 0; w < dimension; w++)
                        {
                            secondDerivative[k][w] += tmp2[k][w];
                        }
                    }
                }
            }

            if (count > 0)
            {
                //N.B: we are computing the negative log likelihood.
                final double invCount = 1.0 / count;

                for (int i = 0; i < dimension; i++)
                {
                    derivative[i] = derivative[i] * invCount;
                    scaledGradient[i] *= invCount;
                    scaledGradient2[i] *= invCount;
                    d2[i] = d2[i] * invCount;
                    jDiag[i] = jDiag[i] * invCount;

                    for (int w = 0; w < dimension; w++)
                    {
                        fisherInformation[i][w] *= invCount;
                    }

                    if (secondDerivative != null)
                    {
                        shiftGradient[i] *= invCount;

                        for (int w = 0; w < dimension; w++)
                        {
                            secondDerivative[i][w] *= invCount;
                        }
                    }
                }
            }
        }

        return new BlockResult(_rowOffset, _rowOffset + count, entropySum, x2,
                derivative, d2, jDiag, shiftGradient, scaledGradient, scaledGradient2, gradientMass, fisherInformation,
                secondDerivative);
    }
}

