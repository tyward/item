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
package edu.columbia.tjw.item.util;

import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.util.FastMath;

/**
 * @author tyler
 */
public final class MultiLogistic
{
    // Power scores cannot be arbitrarily low. This is due to the fact that we only have limited data, there would never
    // be any justification for saying the odds of an event are worse than 1/N for the N observations we have. In this
    // case, use an N deep into the billions, but still low enough to avoid overflow issues.
    private static final double MIN_POWER_SCORE = -30.0;

    /**
     * This should really be something like 1/N for N being the number of observations.
     * Set the default value low, but not insanely low.
     */
    private static final double MIN_PROBABILITY = 1.0e-9;

    private MultiLogistic()
    {
    }

    /**
     * It is safe to pass in the same array for powerScores_ and output_, if you
     * don't mind it being overwritten.
     *
     * @param powerScores_ An array of power scores
     * @param output_      The power scores converted into probabilities
     * @return The normalizing factor for these power scores (essentially, the
     * sum of their exponentials)
     */
    public static double multiLogisticFunction(final double[] powerScores_, final double[] output_)
    {
        final int size = powerScores_.length;
        double maxSum = Double.NEGATIVE_INFINITY;

        for (final double next : powerScores_)
        {
            maxSum = Math.max(next, maxSum);
        }

        double expSum = 0.0;

        for (int i = 0; i < size; i++)
        {
            final double raw = powerScores_[i];
            final double adjusted = raw - maxSum;
            final double floored = Math.max(MIN_POWER_SCORE, adjusted);
            final double exp = FastMath.exp(floored);
            output_[i] = exp;
            expSum += exp;
        }

        final double normalizer = 1.0 / expSum;

        for (int i = 0; i < size; i++)
        {
            output_[i] = output_[i] * normalizer;
        }

        return expSum;
    }

    public static double multiLogisticEntropy(final double[] modelProbabilities_, final int actualIndex_)
    {
        return -Math.log(modelProbabilities_[actualIndex_]);
    }

    /**
     * Calculate the gradient of the entropy of a multi-logistic distribution with respect to the
     * power scores.
     *
     * @param modelProbabilities_ Probabilities produced by the multiLogistic function
     * @param actualIndex_        The index of the actual transition taken by this observation.
     */
    public static void powerScoreEntropyGradient(final double[] modelProbabilities_, final int actualIndex_, final double[] gradOutput_)
    {
        //final double computedProbability = modelProbabilities_[actualIndex_];
        //final double scale = -1.0 / computedProbability;

        for (int i = 0; i < modelProbabilities_.length; i++)
        {
            final double delta;

            if (i == actualIndex_)
            {
                delta = 1.0;
            }
            else
            {
                delta = 0.0;
            }

            gradOutput_[i] = (modelProbabilities_[i] - delta);
        }
    }

    /**
     * Calculate the Hessian of the logistic cross entropy w.r.t. the power scores.
     * <p>
     * We can then use this to quickly calculate the Hessian w.r.t. the params or X values
     * using the chain rule.
     *
     * @param modelProbabilities_ Probabilities produced by the multiLogistic function
     * @param hessianOutput_ The output to hold the computed hessian.
     */
    public static void powerScoreEntropyHessian(final double[] modelProbabilities_,
                                                final double[][] hessianOutput_)
    {
        final int size = modelProbabilities_.length;

        for (int i = 0; i < size; i++)
        {
            final double prob = modelProbabilities_[i];
            hessianOutput_[i][i] = prob * (1.0 - prob);
        }

        for (int i = 0; i < size - 1; i++)
        {
            final double rowProb = modelProbabilities_[i];

            for (int k = i + 1; k < size; k++)
            {
                final double hessianValue = -rowProb * modelProbabilities_[k];
                hessianOutput_[i][k] = hessianValue;
                hessianOutput_[k][i] = hessianValue;
            }
        }
    }

    public static final int chooseOne(final double[] probArray_, final RandomGenerator gen_) {
        double sum = 0.0;
        final double rand = gen_.nextDouble();

        for(int i = 0; i < probArray_.length; i++) {
            sum += probArray_[i];
            if(rand <= sum) {
                return i;
            }
        }

        // Possible due to rounding, but super rare.
        System.err.println("Very rare case hit due to rounding, continuing.");
        return probArray_.length - 1;
    }

//    public static double multiLogisticBetaDerivative(final double[] regressors_,
//                                                     final double[] computedProbabilities_, final int regressorIndex_
//            , final int toStateIndex_, final int toStateBetaIndex_)
//    {
//        final double reg = regressors_[regressorIndex_];
//        final double computedProb = computedProbabilities_[toStateIndex_];
//        final double betaComputedProb = computedProbabilities_[toStateBetaIndex_];
//
//        double inner;
//
//        if (toStateIndex_ == toStateBetaIndex_)
//        {
//            inner = 1.0;
//        }
//        else
//        {
//            inner = 0.0;
//        }
//
//        inner = inner - betaComputedProb;
//        final double result = reg * inner * computedProb;
//        return result;
//    }
//
//    public static void multiLogisticRegressorDerivatives(final double[] powerScores_, final double[] betaValues_,
//                                                         final double[] workspace_, final double[] output_)
//    {
//        final double expSum = multiLogisticFunction(powerScores_, workspace_);
//
//        //Workspace now holds probabilities.
//        double betaSum = 0.0;
//
//        for (int i = 0; i < powerScores_.length; i++)
//        {
//            //A term of the form exp(beta dot x).
//            final double expTerm = workspace_[i] * expSum;
//
//            //This is the term beta_(i)(regressorIndex_)
//            final double betaValue = betaValues_[i];
//
//            final double betaMultiplied = betaValue * expTerm;
//
//            betaSum += betaMultiplied;
//
//            output_[i] = betaValue;
//        }
//
//        final double normalizedTerm = betaSum / expSum;
//
//        for (int i = 0; i < powerScores_.length; i++)
//        {
//            output_[i] = (output_[i] - normalizedTerm) * workspace_[i];
//        }
//
//        //Done, output holds the results.
//    }

    /**
     * It is safe to pass the same array to both arguments if you don't mind it
     * being overwritten.
     *
     * @param baseCase_      Which power score will be set to zero.
     * @param probabilities_ An array of probabilities
     * @param output_        The corresponding power scores, with output_[baseCase_] =
     *                       0.0
     */
    public static void multiLogitFunction(final int baseCase_, final double[] probabilities_, final double[] output_)
    {
        final int size = probabilities_.length;

        final double baseProbability = probabilities_[baseCase_];
        final double invBaseProbability = 1.0 / baseProbability;

        for (int i = 0; i < size; i++)
        {
            final double logOdds = probabilities_[i] * invBaseProbability;
            final double powerScore = Math.log(logOdds);
            output_[i] = powerScore;
        }
    }

}
