/*
 * Copyright 2014 Tyler Ward.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * This code is part of the reference implementation of http://arxiv.org/abs/1409.6075
 * 
 * This is provided as an example to help in the understanding of the ITEM model system.
 */
package edu.columbia.tjw.item.optimize;

/**
 *
 * @author tyler
 */
public final class MultivariateFiniteDiffDerivFunction implements MultivariateDifferentiableFunction
{
    private static final double DEFAULT_SCALE = 0.01;
    private static final double SCALE_CUTOFF = 1.0;

    private final MultivariateFunction _function;
    private final EvaluationResult[] _resultsPlus;
    private final EvaluationResult[] _resultsMinus;
    private final MultivariatePoint _center;
    private final double[] _scale;
    private final MultivariatePoint[] _samplePointsPlus;
    private final MultivariatePoint[] _samplePointsMinus;
    private final AdaptiveComparator<MultivariatePoint, MultivariateFunction> _comparator;

    public MultivariateFiniteDiffDerivFunction(final MultivariateFunction function_, final AdaptiveComparator<MultivariatePoint, MultivariateFunction> comparator_)
    {
        _function = function_;
        final int dimension = function_.dimension();

        _resultsPlus = new EvaluationResult[dimension];
        _resultsMinus = new EvaluationResult[dimension];
        _comparator = comparator_;

        _center = new MultivariatePoint(dimension);
        _scale = new double[dimension];
        _samplePointsPlus = new MultivariatePoint[dimension];
        _samplePointsMinus = new MultivariatePoint[dimension];

        for (int i = 0; i < dimension; i++)
        {
            _resultsPlus[i] = function_.generateResult();
            _resultsMinus[i] = function_.generateResult();
            _scale[i] = DEFAULT_SCALE;
            _samplePointsPlus[i] = new MultivariatePoint(dimension);
            _samplePointsMinus[i] = new MultivariatePoint(dimension);
        }
    }

    @Override
    public int dimension()
    {
        return _function.dimension();
    }

    @Override
    public void value(MultivariatePoint input_, int start_, int end_, EvaluationResult result_)
    {
        _function.value(input_, start_, end_, result_);
    }

    @Override
    public int numRows()
    {
        return _function.numRows();
    }

    @Override
    public MultivariateGradient calculateDerivative(MultivariatePoint input_, EvaluationResult result_, double precision_)
    {
        final int dimension = this.dimension();

        if (!_center.equals(input_))
        {
            _center.copy(input_);

            for (int i = 0; i < dimension; i++)
            {
                resetDirection(i, input_, _scale[i]);
            }
        }

        final MultivariatePoint derivative = new MultivariatePoint(this.dimension());
        final MultivariatePoint secondDerivative = new MultivariatePoint(this.dimension());
        boolean goodScales = true;

        for (int i = 0; i < dimension; i++)
        {
            final boolean scaleGood = this.computeOneDerivative(i, input_, result_, derivative, secondDerivative, false);
            goodScales = goodScales && scaleGood;
        }

        //Now we compute our derivative.....
        double theta = this.computeTheta(derivative, input_, result_);

        final int numRows = this.numRows();

        //We have a derivative, and have chosen the best scale we can, but the derivative may not be
        //accurate enough. Simply do additional calculations if needed. 
        while (theta > precision_)
        {
            //We need to upgrade some of our results.....
            //Figure out what has the highest std. dev, and do more calculations on it. 
            int target = -1;
            double stdDev = Double.NEGATIVE_INFINITY;

            for (int i = 0; i < dimension; i++)
            {
                final double plusDev = _resultsPlus[i].getStdDev();
                final double minusDev = _resultsMinus[i].getStdDev();
                final double nextDev = Math.max(plusDev, minusDev);

                if (nextDev > stdDev)
                {
                    final int countPlus = _resultsPlus[i].getHighWater();
                    final int countMinus = _resultsMinus[i].getHighWater();

                    if ((countPlus >= numRows) && (countMinus >= numRows))
                    {
                        //Can't do anything about this dimension, move on. 
                        continue;
                    }

                    target = i;
                }
            }

            if (-1 == target)
            {
                //We have used all our data, still not good enough, get out. 
                break;
            }

            final int posStart = _resultsPlus[target].getHighWater();
            final int negStart = _resultsMinus[target].getHighWater();

            final int maxStart = Math.max(posStart, negStart);
            final int endPoint = Math.min(2 * maxStart, numRows);

            //Do more calculations to improve our estimates. 
            this.value(_samplePointsPlus[target], posStart, endPoint, _resultsPlus[target]);
            this.value(_samplePointsMinus[target], negStart, endPoint, _resultsMinus[target]);

            //Compute theta again....
            theta = this.computeTheta(derivative, input_, result_);
        }

        //Theta may not be small enough, but if that is the case, then we have exhausted all our calculations so there is nothing more we can do. 
        final MultivariateGradient gradient = new MultivariateGradient(input_, derivative, secondDerivative, theta);
        return gradient;
    }

    private final boolean compareMagnitude(final double input_)
    {
        return (Math.abs(input_) < this._comparator.getSigmaTarget());
    }

    /**
     *
     * @param index_
     * @param input_
     * @param result_
     * @param derivative_
     * @param secondDerivative_
     * @return True if there were no unresolved issues with scale.
     */
    private boolean computeOneDerivative(final int index_, final MultivariatePoint input_, final EvaluationResult result_, final MultivariatePoint derivative_,
            final MultivariatePoint secondDerivative_, final boolean isUpdate_)
    {
        double comparisonPlus = 0.0;
        double comparisonMinus = 0.0;
        double comparison = 0.0;

        final double startingScale = _scale[index_];
        double scale = startingScale;

        if (!isUpdate_)
        {
            //Realistically, we only compute each of these comparisons once, unless the scale is bad, in which case it could take a few tries. 
            while (compareMagnitude(comparisonMinus) || compareMagnitude(comparisonPlus) || compareMagnitude(comparison))
            {
                if (scale > SCALE_CUTOFF)
                {
                    break;
                }

                this.resetDirection(index_, input_, scale);
                _scale[index_] = scale;
                scale *= 2.0;

                comparisonPlus = _comparator.compare(this, input_, _samplePointsPlus[index_], result_, _resultsPlus[index_]);

                if ((scale <= SCALE_CUTOFF) && compareMagnitude(comparisonPlus))
                {
                    continue;
                }

                comparisonMinus = _comparator.compare(this, input_, _samplePointsMinus[index_], result_, _resultsMinus[index_]);

                if ((scale <= SCALE_CUTOFF) && compareMagnitude(comparisonMinus))
                {
                    continue;
                }

                comparison = _comparator.compare(this, _samplePointsPlus[index_], _samplePointsMinus[index_], _resultsPlus[index_], _resultsMinus[index_]);
            }
        }

        System.out.println("Computing one derivative: " + index_ + " [" + startingScale + ", " + scale + "]");

        //We either have all comparisons nonzero, or we have exceeded the scale cutoff...
        final double x0 = input_.getElement(index_);
        final double xPlus = _samplePointsPlus[index_].getElement(index_);
        final double xMinus = _samplePointsMinus[index_].getElement(index_);

        final double y0 = result_.getMean();
        final double yPlus = _resultsPlus[index_].getMean();
        final double yMinus = _resultsMinus[index_].getMean();

        //final double dev0 = result_.getMeanStdDev();
        //final double devPlus = _resultsPlus[index_].getMeanStdDev();
        //final double devMinus = _resultsMinus[index_].getMeanStdDev();
        final double h = (xPlus - xMinus);
        final double h2 = h * h;
        final double dydx = (yPlus - yMinus) / h;

        //final double dydxDev = Math.sqrt(devPlus * devPlus + devMinus * devMinus);
        final double dy2dx = (yPlus + yMinus - (2.0 * y0)) / (h * h);

        final double distanceToZero = dydx / dy2dx;
        final double dtz2 = distanceToZero * distanceToZero;

        final boolean distanceCheck = dtz2 > h2;
        final boolean scaleIncreased = (scale > startingScale);

        if ((!isUpdate_) && (!distanceCheck))
        {
            //The scale is too large, the root is projected to be closer than the steps used for our finite difference derivative. 
            //We need to adjust the scale downward

            //If we increased the scale, then this is the best we can do, we had to increase the scale to get an accurate reading, so we can't reduce it....
            //Otherwise, lets scale things down and try again.
            if (!scaleIncreased)
            {
                final double distanceRatio = dtz2 / h2;
                final double newScale = scale * distanceRatio * 0.5;
                _scale[index_] = newScale;

                System.out.println("Scale too large, decreasing.");

                computeOneDerivative(index_, input_, result_, derivative_, secondDerivative_, isUpdate_);
                return false;
            }
        }

        //Either the distances check out, or there's nothing we can do about them. Do the calculation and move on.
        derivative_.setElement(index_, dydx);
        secondDerivative_.setElement(index_, dy2dx);

        //Return true if we had no scale problems at all.
        final boolean retVal = distanceCheck && (scale == startingScale);
        return retVal;
    }

    private double computeTheta(final MultivariatePoint derivative_, MultivariatePoint input_, final EvaluationResult result_)
    {
        final int dimension = this.dimension();

        double devDot = 0.0;
        double dxMag = 0.0;
        double devMag = 0.0;
        double crossDot = 0.0;

        //Try to compute vectors dx and dx2 such that they differ by only a vector of one standard deviation
        //that is also roughly perpendicular to dx. Then compute the angle between these vectors. 
        //That is theta, and we will attempt to limit the size of theat (e.g. by computing more terms) to make sure
        //our derivative is pointing in roughly the right direction.
        for (int i = 0; i < dimension; i++)
        {
            final double dx = derivative_.getElement(i);

            final double var1 = _resultsPlus[i].getVariance();
            final double var2 = _resultsMinus[i].getVariance();
            final double dev = Math.sqrt(var1 + var2);

            final double dotBase = dev * dx;

            final boolean signMismatch = (devDot > 0.0) ^ (dotBase > 0.0);
            final double dx2Elem;

            if (signMismatch)
            {
                devDot += dotBase;
                dx2Elem = dx + dev;
            }
            else
            {
                devDot -= dotBase;
                dx2Elem = dx - dev;
            }

            crossDot = dx2Elem * dx;
            dxMag += (dx * dx);
            devMag += (dev * dev);
        }

        dxMag = Math.sqrt(dxMag);
        devMag = Math.sqrt(devMag);

        final double cosTheta = crossDot / (dxMag * devMag);

        final double theta = Math.acos(cosTheta);

        return theta;
    }

    private void resetDirection(final int index_, final MultivariatePoint input_, final double scale_)
    {
        _resultsPlus[index_].clear();
        _resultsMinus[index_].clear();

        _samplePointsPlus[index_].copy(input_);
        _samplePointsMinus[index_].copy(input_);

        final double inputValue = input_.getElement(index_);

        final double adjustment = (1.0 + Math.abs(inputValue)) * scale_;
        final double plusValue = inputValue + adjustment;
        final double minusValue = inputValue - adjustment;

        _samplePointsPlus[index_].setElement(index_, plusValue);
        _samplePointsMinus[index_].setElement(index_, minusValue);
    }

    @Override
    public EvaluationResult generateResult(int start_, int end_)
    {
        return this._function.generateResult(start_, end_);
    }

    @Override
    public EvaluationResult generateResult()
    {
        return this.generateResult(0, this.numRows());
    }

}
