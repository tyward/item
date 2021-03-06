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

import edu.columbia.tjw.item.ItemSettings;
import edu.columbia.tjw.item.algo.DoubleVector;
import edu.columbia.tjw.item.algo.VectorTools;
import edu.columbia.tjw.item.fit.calculator.FitPoint;
import edu.columbia.tjw.item.fit.calculator.FitPointAnalyzer;
import edu.columbia.tjw.item.util.LogUtil;

import java.util.logging.Logger;

/**
 * @author tyler
 */
public class MultivariateOptimizer extends Optimizer<MultivariateDifferentiableFunction>
{
    private static final double STD_DEV_CUTOFF = 1.0;
    private static final double LINE_SEARCH_XTOL = Math.sqrt(Math.ulp(1.0));
    private static final double LINE_SEARCH_YTOL = Math.sqrt(Math.ulp(1.0));
    private static final double SCALE_MULTIPLE = 0.1;
    private static final Logger LOG = LogUtil.getLogger(MultivariateOptimizer.class);

    private final double _zTolerance;
    private final GoldenSectionOptimizer _optimizer;

    public MultivariateOptimizer(final int blockSize_, int maxEvalCount_, final int loopEvalCount_,
                                 final double thetaPrecision_, final OptimizationTarget target_, ItemSettings settings_)
    {
        super(blockSize_, maxEvalCount_, target_, settings_);

        if (thetaPrecision_ < 0.0 || thetaPrecision_ > Math.PI)
        {
            throw new IllegalArgumentException("Invalid theta: " + thetaPrecision_);
        }

        if (maxEvalCount_ < 10 * loopEvalCount_)
        {
            throw new IllegalArgumentException("MaxEvalCount must be significantly larger than the loop count.");
        }

        _zTolerance = settings_.getZScoreCutoff();
        _optimizer = new GoldenSectionOptimizer(LINE_SEARCH_XTOL, LINE_SEARCH_YTOL, blockSize_, loopEvalCount_,
                target_, settings_);
    }

    @Override
    public OptimizationResult optimize(MultivariateDifferentiableFunction f_,
                                       DoubleVector startingPoint_,
                                       DoubleVector direction_) throws ConvergenceException
    {
        final FitPoint result = f_.evaluate(startingPoint_);
        return optimize(f_, startingPoint_, result, direction_);
    }

    public OptimizationResult optimize(MultivariateDifferentiableFunction f_,
                                       DoubleVector startingPoint_) throws ConvergenceException
    {
        final FitPoint result = f_.evaluate(startingPoint_);

        // Testing code.
        final FitPoint point = f_.evaluateGradient(startingPoint_);
        final DoubleVector derivative = this.getComparator().getDerivative(point);

        final MultivariateGradient gradient = new MultivariateGradient(derivative, null);
        final DoubleVector direction = VectorTools.scalarMultiply(gradient.getGradient(), -1.0);

        return optimize(f_, startingPoint_, result, direction);
    }

    public OptimizationResult optimize(MultivariateDifferentiableFunction f_,
                                       DoubleVector startingPoint_,
                                       final FitPoint result_,
                                       DoubleVector direction_) throws ConvergenceException
    {
        DoubleVector direction = direction_;
        DoubleVector currentPoint = startingPoint_;
        FitPoint currentResult = result_;

        final int maxEvalCount = this.getMaxEvalCount();
        final int dimension = f_.dimension();
        int evaluationCount = 0;

        final FitPointAnalyzer comparator = this.getComparator();
        DoubleVector nextPoint = currentPoint;

        double stepMagnitude = Double.NaN;
        boolean xTolExceeded = true;
        boolean yTolExceeded = true;
        boolean firstLoop = true;
        FitPoint fitPointPrev = null;

        try
        {
            while (xTolExceeded && yTolExceeded && (evaluationCount < maxEvalCount))
            {
                //final FitPoint fitPointCurrent = f_.evaluate(currentPoint);
                final OptimizationResult result;

                if (!firstLoop)
                {
                    final FitPoint point = f_.evaluateGradient(currentPoint);
                    final DoubleVector derivative = this.getComparator().getDerivative(point, fitPointPrev);

                    final MultivariateGradient gradient = new MultivariateGradient(derivative, null);

                    evaluationCount += (2 * dimension);

                    final DoubleVector trialPoint;
                    final FitPoint trialRes;

                    final DoubleVector pointA = VectorTools.scalarMultiply(gradient.getGradient(), -1.0);
                    //pointA.scale(-1.0);

                    if (null == gradient.getSecondDerivative())
                    {
                        //trialPoint = pointA;
                        //trialRes = f_.evaluate(trialPoint);

                        //We need to control the magnitude of the root bracketing....
                        //We want this small enough that we are searching in a small interval, but not so small that
                        //we need to spend a lot of time to expand the interval. Err on the side of smallness, since
                        // expanding
                        //and contracting the interval are about the same cost and typically we won't need much.
                        final double directionMagnitude = VectorTools.magnitude(pointA);
                        final double desiredMagnitude = stepMagnitude * SCALE_MULTIPLE;
                        final double scale = desiredMagnitude / directionMagnitude;

                        if (directionMagnitude < 1.0e-8)
                        {
                            LOG.info("Ambiguous derivative, root search done.");
                            break;
                        }

                        //LOG.info("Rescaled direction: " + scale);
                        trialPoint = VectorTools.multiplyAccumulate(currentPoint, pointA, scale);
                        //trialPoint.add(currentPoint);
                    }
                    else
                    {
                        throw new UnsupportedOperationException("Not  yet implemented.");
//                        final DoubleVector pointB = gradient.getSecondDerivative();
//                        //final DoubleVector
//
//
//                        for (int i = 0; i < dimension; i++)
//                        {
//                            final double aVal = pointA.getEntry(i);
//                            final double bVal = pointB.getEntry(i);
//
//                            final double presumedZero = -1.0 * (aVal / bVal);
//
//                            pointB.setElement(i, presumedZero);
//                        }
//
//                        pointA.scale(-1.0);
//
//                        pointA.add(currentPoint);
//                        pointB.add(currentPoint);
//
//                        final FitPoint fitPointA = f_.evaluate(pointA.getElements());
//                        final FitPoint fitPointB = f_.evaluate(pointB.getElements());
//
//                        //Only take it if it is clearly better.....
//                        final FitPointAnalyzer.FitPointComparison comparison = comparator
//                                .generateComparision(fitPointA, fitPointB);
//
//                        if (comparison.getZScore() <= -comparator.getSigmaTarget())
//                        {
//                            //The straight derivative point is better....
//                            trialPoint = pointA;
//                            trialRes = fitPointA;
//                        }
//                        else
//                        {
//                            final FitPointAnalyzer.FitPointComparison comp2 = comparator.generateComparision(
//                                    currentResult, fitPointB);
//
//                            if (comp2.getZScore() <= -comparator.getSigmaTarget())
//                            {
//                                //The second derivative point is no better than the current point, use the standard
//                                // derivative.
//                                trialPoint = pointA;
//                                trialRes = fitPointA;
//                            }
//                            else
//                            {
//                                trialPoint = pointB;
//                                trialRes = fitPointB;
//                            }
//                        }
                    }

                    final DoubleVector dir = VectorTools.subtract(trialPoint, currentPoint);
                    final UnivariateOptimizationFunction func = new UnivariateOptimizationFunction(f_, currentPoint,
                            dir);

                    result = _optimizer
                            .optimize(func, 0.0, currentResult,
                                    1.0,
                                    f_.evaluate(trialPoint));
                }
                else
                {
                    firstLoop = false;
                    result = _optimizer.optimize(new UnivariateOptimizationFunction(f_, currentPoint, direction));
                }

                evaluationCount += result.evaluationCount();

                nextPoint = result.getOptimum().collapse();
                final FitPoint nextResult = result.minResult();

                final FitPointAnalyzer.FitPointComparison comparison = comparator.generateComparision(
                        currentResult, nextResult);

                final double zScore = comparison.getZScore();

                //LOG.info("Finished one line search: " + zScore);
                if (zScore <= _zTolerance)
                {
                    LOG.info("Unable to make progress.");
                    currentResult = nextResult;
                    currentPoint = nextPoint;
                    break;
                }

                yTolExceeded = !(comparison.getRelativeError() < this.getYTolerance());


                //yTolExceeded = !this.checkYTolerance(currentResult, nextResult);
                xTolExceeded = !this.checkXTolerance(currentPoint, nextPoint);

                stepMagnitude = VectorTools.distance(currentPoint, nextPoint);
                currentPoint = nextPoint;
                fitPointPrev = currentResult;
                currentResult = nextResult;
            }
        }
        catch (final ConvergenceException e)
        {
            LOG.info("Covergence exception, continuing: ");
        }

        //Did we converge, or did we run out of iterations. 
        final boolean converged = (!xTolExceeded || !yTolExceeded);
        final MultivariateOptimizationResult output = new MultivariateOptimizationResult(currentResult, converged,
                evaluationCount);
        return output;
    }

}
