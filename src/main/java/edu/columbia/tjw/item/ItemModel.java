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
package edu.columbia.tjw.item;

import edu.columbia.tjw.item.fit.ItemParamGrid;
import edu.columbia.tjw.item.fit.PackedParameters;
import edu.columbia.tjw.item.fit.ParamFittingGrid;
import edu.columbia.tjw.item.util.LogLikelihood;
import edu.columbia.tjw.item.util.LogUtil;
import edu.columbia.tjw.item.util.MultiLogistic;

import java.util.Arrays;
import java.util.logging.Logger;

/**
 * A class representing an ITEM model.
 * <p>
 * Note that this class is not threadsafe. You need to synchronize on your own,
 * or clone this model and use the clone in other threads.
 *
 * @param <S> The status type for this model
 * @param <R> The regressor type for this model
 * @param <T> The curve type for this model
 * @author tyler
 */
public final class ItemModel<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
        implements Cloneable
{
    private static final Logger LOG = LogUtil.getLogger(ItemModel.class);
    private final double ROUNDING_TOLERANCE = 1.0e-8;
    private final LogLikelihood<S> _likelihood;

    // We need both params and packed because in some cases we will want derivatives with respect to only subsets of
    // the parameters. However, we allow users to specify only one or the other, so they are guaranteed consistent at
    // this point.
    private final ItemParameters<S, R, T> _params;
    private final PackedParameters<S, R, T> _packed;

    private final double[][] _betas;
    private final int _reachableSize;

    private final double[] _rawRegWorkspace;
    private final double[] _regWorkspace;
    private final double[] _probWorkspace;
    private final double[] _psDerivativeWorkspace;

    // These workspace hold the derivative of the entropy w.r.t. the power scores,
    // and the hessian of same. This is used to compute gradient and hessian w.r.t. the
    // parameters (or regressors, theoretically) using the chain rule.
    private final double[] _entropyScoreDerivative;
    private final double[][] _entropyScoreHessian;




    /**
     * Create a new item model from its parameters.
     *
     * @param params_ The parameters that define this model
     */
    public ItemModel(final ItemParameters<S, R, T> params_)
    {
        this(params_, params_.generatePacked());
    }

    public ItemModel(final PackedParameters<S, R, T> packed_)
    {
        // We need to clone here so that nobody can be adjusting our packed params from outside.
        this(packed_.generateParams(), packed_.clone());
    }

    private ItemModel(final ItemParameters<S, R, T> params_, PackedParameters<S, R, T> packed_)
    {
        synchronized (this)
        {
            //This is synchronized so that clone may safely be called by any thread.
            //In this way, we can generate models that are detached from one another,
            //and may safely be used simultaneously in different threads without requiring
            //any additional synchronization.
            _params = params_;
            _packed = packed_;
            final S status = params_.getStatus();
            _betas = params_.getBetas();
            _reachableSize = status.getReachableCount();

            _likelihood = new LogLikelihood<>(status);

            final int entryCount = params_.getEntryCount();

            _rawRegWorkspace = new double[params_.getUniqueRegressors().size()];
            _regWorkspace = new double[entryCount];
            _probWorkspace = new double[_reachableSize];
            _psDerivativeWorkspace = new double[_packed.size()];

            _entropyScoreDerivative = new double[_reachableSize];
            _entropyScoreHessian = new double[_reachableSize][_reachableSize];
        }
    }


    public S getStatus()
    {
        return _params.getStatus();
    }

    public final ItemParameters<S, R, T> getParams()
    {
        return _params;
    }

    public int getDerivativeSize()
    {
        return _packed.size();
    }

    /**
     * N.B: This is NOT threadsafe! Generate a new model for each thread, there
     * is some internal workspace associated that can't be shared.
     *
     * @param grid_  The grid holding the data
     * @param index_ The row of the grid on which to apply the model
     * @return The log likelihood of the data point represented by this row
     */
    public final double logLikelihood(final ParamFittingGrid<S, R, T> grid_, final int index_)
    {
        final double[] computed = _probWorkspace;
        transitionProbability(grid_, index_, computed);

        final int actualOutcome = grid_.getNextStatus(index_);
        final int mapped = _likelihood.ordinalToOffset(actualOutcome);

        //If this item took a forbidden transition, ignore the data point.
        if (mapped < 0)
        {
            return 0.0;
        }

        final double logLikelihood = _likelihood.logLikelihood(computed, mapped);
        return logLikelihood;
    }

    private double computeEntryWeight(final double[] rawRegressors_, final int entry_)
    {
        if (rawRegressors_.length != _rawRegWorkspace.length)
        {
            throw new IllegalArgumentException("Length mismatch.");
        }

        if (entry_ == _params.getInterceptIndex())
        {
            // The intercept doesn't even need a regressor.
            return 1.0;
        }

        final int depth = _params.getEntryDepth(entry_);
        double weight = 1.0;

        for (int w = 0; w < depth; w++)
        {
            final double rawReg = _params.getEntryRegressorValue(entry_, w, rawRegressors_);
            final ItemCurve<T> curve = _params.getEntryCurve(entry_, w);

            if (null == curve)
            {
                weight *= rawReg;
            }
            else
            {
                weight *= curve.transform(rawReg);
            }
        }

        return weight;
    }

    /**
     * Adds the entry power scores to the output_ vector. designed to make it easy to loop through entries. Set
     * output_ to zero when needed.
     *
     * @param rawRegressors_
     * @param entry_
     * @param powerScoreOutput_
     */
    private void addEntryPowerScores(final double[] rawRegressors_, final int entry_, final double[] powerScoreOutput_)
    {
        if (powerScoreOutput_.length != _betas.length)
        {
            throw new IllegalArgumentException("Mismatch.");
        }

        final double weight = computeEntryWeight(rawRegressors_, entry_);
        for (int i = 0; i < _betas.length; i++)
        {
            final double beta = _betas[i][entry_];
            final double score = weight * beta;
            powerScoreOutput_[i] += score;
        }
    }


    private void fillEntryWeights(final double[] rawRegressors_, final double[] weightOutput_)
    {
        final int numEntries = _params.getEntryCount();

        if (weightOutput_.length != numEntries)
        {
            throw new IllegalArgumentException("Length mismatch.");
        }

        for (int i = 0; i < numEntries; i++)
        {
            weightOutput_[i] = computeEntryWeight(rawRegressors_, i);
        }
    }


    public void computeGradient(final ParamFittingGrid<S, R, T> grid_,
                                final int index_, final double[] derivative_,
                                final double[] jDiag_, final double[][] secondDerivative_)
    {
        final int dimension = _packed.size();

        if (derivative_.length != dimension)
        {
            throw new IllegalArgumentException("Derivative size mismatch.");
        }

        final double[] modelProbabilities = _probWorkspace;
        final double[] powerScoreDerivatives = _psDerivativeWorkspace;
        final double[] entryWeights = _regWorkspace;
        final double[] rawReg = _rawRegWorkspace;

        //We inline a bunch of these calcs to reduce duplication of effort.
        grid_.getRegressors(index_, rawReg);
        this.fillEntryWeights(rawReg, entryWeights);
        rawPowerScores(entryWeights, modelProbabilities);
        MultiLogistic.multiLogisticFunction(modelProbabilities, modelProbabilities);

        // don't loop over this stuff, just do the one transition we know happened.
        final int actualTransition = grid_.getNextStatus(index_);
        final int actualOffset = _likelihood.ordinalToOffset(actualTransition);

        if (actualOffset < 0)
        {
            Arrays.fill(derivative_, 0.0);
            return;
        }

        // We will use the chain rule. We have h(s(Theta)), so first compute dh/ds, then multiply
        // by ds/dTheta to get dh/dTheta
        MultiLogistic.powerScoreEntropyGradient(modelProbabilities, actualOffset, _entropyScoreDerivative);


        for (int k = 0; k < dimension; k++)
        {
            final int entry = _packed.getEntry(k);
            final boolean isBetaDerivative = _packed.isBeta(k);
            final int derivToStatus = _packed.getTransition(k);
            final double derivCore = _entropyScoreDerivative[derivToStatus];

            final double entryWeight = this.computeEntryWeight(rawReg, entry);

            if (entryWeight != entryWeights[entry])
            {
                throw new IllegalStateException("Error.");
            }

            final double pDeriv;

            if (isBetaDerivative)
            {
                pDeriv = entryWeight;
            }
            else
            {
                // This is a derivative w.r.t. one of the elements of the weight.
                // N.B: We know the weight will only apply to a single transition, greatly simplifying the calculation.
                final double entryBeta2 = _packed.getEntryBeta(k);

                final double dw2 = computeWeightDerivative(rawReg, k, entryWeight, entry);
                pDeriv = entryBeta2 * dw2;
            }

            powerScoreDerivatives[k] = pDeriv;
            derivative_[k] = derivCore * pDeriv;
        }

        if (null != secondDerivative_ || null != jDiag_)
        {
            fillSecondDerivatives(rawReg, actualOffset,
                    modelProbabilities,
                    powerScoreDerivatives, _entropyScoreDerivative,
                    jDiag_, secondDerivative_);
        }
    }

    private double computeWeightDerivative(final double[] x_, final int k, double entryWeight_,
                                           final int entry_)
    {
        // This is a derivative w.r.t. one of the elements of the weight.
        // N.B: We know the weight will only apply to a single transition, greatly simplifying the calculation.
        //final double entryBeta = packed_.getParameter(k);
        final int curveDepth = _packed.getDepth(k);
        final ItemCurve<T> curve = _params.getEntryCurve(entry_, curveDepth);

        final double reg = _params.getEntryRegressorValue(entry_, curveDepth, x_);//x_[regOffset];

        final int curveParamIndex = _packed.getCurveIndex(k);

        final double curveValue = curve.transform(reg);
        final double curveDeriv = curve.derivative(curveParamIndex, reg);

        if (curveDeriv == 0.0)
        {
            // Dealing with some special over/underflow cases.
            // This can't actually be zero.
            return 0.0;
        }
        else
        {
            final double valRatio = curveDeriv / curveValue;

            final double dw = entryWeight_ * valRatio;
            return dw;
        }
    }


    private void fillSecondDerivatives(final double[] x_, final int actualOffset_,
                                       final double[] modelProbabilities_, final double[] pDeriv_,
                                       double[] scoreGradient_, final double[] jDiag_,
                                       double[][] secondDerivative_)
    {
        if (null != secondDerivative_ && secondDerivative_.length != jDiag_.length)
        {
            throw new IllegalArgumentException("Mismatched sizes! " + secondDerivative_.length +
                    " != " + jDiag_.length);
        }

        final int outputSize = modelProbabilities_.length;

        // Also compute second derivatives using the chain rule, it's just a little bit more complicated.
        MultiLogistic.powerScoreEntropyHessian(modelProbabilities_, _entropyScoreHessian);

        // We will use the chain rule. We have h(s(Theta)), so first compute dh/ds, then multiply
        // by ds/dTheta to get dh/dTheta

        final int derivSize = jDiag_.length;

        for (int w = 0; w < derivSize; w++)
        {
            final int wStatus = _packed.getTransition(w);
            final int entryW = _packed.getEntry(w);
            final double pw = pDeriv_[w];
            final double psHessDiag = _entropyScoreHessian[wStatus][wStatus];
            final double psGradW = scoreGradient_[wStatus];

            final double diag1 = pw * pw * psHessDiag;

            // N.B: We know wToStatus == zToStatus because their entries match and they have at least one curve
            // (otherwise both are betas, and this is zero).
            final double psdDiag = powerScoreSecondDerivative(x_, w, w, wStatus, entryW);
            final double diag2 = psdDiag * psGradW;

            final double diagVal = diag1 + diag2;
            jDiag_[w] = diagVal;

            if (null != secondDerivative_)
            {
                secondDerivative_[w][w] = diagVal;

                // Fill the off diagonal elements if needed.
                for (int w2 = w + 1; w2 < derivSize; w2++)
                {
                    final int w2Status = _packed.getTransition(w2);
                    final double pw2 = pDeriv_[w2];
                    final double ds2 = _entropyScoreHessian[wStatus][w2Status];
                    final int entryW2 = _packed.getEntry(w2);

                    final double term1 = pw * pw2 * ds2;
                    final double term2;

                    if (entryW != entryW2)
                    {
                        // These are different entries, the second derivative is zero (taking derivative
                        // w.r.t. something that isn't a parameter of this entry)
                        term2 = 0.0;
                    }
                    else
                    {
                        // N.B: We know wToStatus == zToStatus because their entries match and they have at least one curve
                        // (otherwise both are betas, and this is zero).
                        final double psd = powerScoreSecondDerivative(x_, w, w2, wStatus, entryW);
                        term2 = psd * psGradW;
                    }

                    final double hessianEntry = term1 + term2;
                    secondDerivative_[w][w2] = hessianEntry;
                    secondDerivative_[w2][w] = hessianEntry;
                }
            }
        }
    }

    private double powerScoreSecondDerivative(final double[] x_, final int w, final int z, final int toStatus_,
                                              final int entry_)
    {
        final boolean isBetaW = _packed.isBeta(w);
        final boolean isBetaZ = _packed.isBeta(z);

        if (isBetaW && isBetaZ)
        {
            // This is a second derivative with respect to a beta (i.e. linear term), hence zero.
            return 0.0;
        }

        final double entryWeight = this.computeEntryWeight(x_, entry_);

        if (isBetaW)
        {
            // This is a single derivative w.r.t. a single curve and also its beta. Hence just the derivative w.r.t.
            // the weights.
            final double dw = computeWeightDerivative(x_, z, entryWeight, entry_);
            return dw;
        }
        else if (isBetaZ)
        {
            // Same thing, just reversed.
            final double dw = computeWeightDerivative(x_, w, entryWeight, entry_);
            return dw;
        }

        // OK, neither of these is a beta derivative, both are on curves.
        // Could use either w or z, get the same result.
        final double entryBeta = _packed.getEntryBeta(w);
        final int curveDepthW = _packed.getDepth(w);
        final int curveDepthZ = _packed.getDepth(z);
        final int curveParamW = _packed.getCurveIndex(w);
        final int curveParamZ = _packed.getCurveIndex(z);

        final ItemCurve<T> curveW = _params.getEntryCurve(entry_, curveDepthW);
        final double regW = _params.getEntryRegressorValue(entry_, curveDepthW, x_);//x_[regOffsetW];
        final double curveValueW = curveW.transform(regW);

        if (curveDepthW == curveDepthZ)
        {
            // This is a single derivative w.r.t. a single curve and also its beta. Hence just the derivative w.r.t.
            // the weights.
            final double curveSecondDeriv = curveW.secondDerivative(curveParamW, curveParamZ, regW);

            if (curveSecondDeriv == 0.0)
            {
                return 0.0;
            }

            // Just replace the curve value with its second derivative in the entry.
            return entryBeta * entryWeight * (curveSecondDeriv / curveValueW);
        }
        else
        {
            // These are different curves, need to swap out both of them.
            final ItemCurve<T> curveZ = _params.getEntryCurve(entry_, curveDepthZ);
            final double regZ = _params.getEntryRegressorValue(entry_, curveDepthZ, x_);
            final double curveValueZ = curveZ.transform(regZ);

            final double derivW = curveW.derivative(curveParamW, regW);
            final double derivZ = curveZ.derivative(curveParamZ, regZ);

            if (derivW == 0.0)
            {
                return 0.0;
            }
            if (derivZ == 0.0)
            {
                return 0.0;
            }

            return entryBeta * entryWeight * (derivW / curveValueW) * (derivZ / curveValueZ);
        }
    }

    /**
     * N.B: This is NOT threadsafe. It contains some use of internal state that
     * cannot be shared. Clone the model as needed.
     *
     * @param grid_   The grid holding the data
     * @param index_  The row in the grid representing this data point
     * @param output_ The array to hold the output transition probabilities
     * @return The number of probabilities written into output_
     */
    public int transitionProbability(final ItemParamGrid<S, R, T> grid_, final int index_, final double[] output_)
    {
        grid_.getRegressors(index_, _rawRegWorkspace);
        return transitionProbability(_rawRegWorkspace, output_);
    }

    /**
     * Compute the transition probability from raw regressors. Be fairly careful
     * here, it's not easy to know which order the regressors need to come in.
     *
     * @param regs_   The raw regressor values (ordered to match
     *                uniqueRegressors() from the ItemParams under this model)
     * @param output_ A workspace to hold the resulting transition
     *                probabilities.
     * @return The number of transition probabilities.
     */
    public int transitionProbability(final double[] regs_, final double[] output_)
    {
        multiLogisticFunction(regs_, output_);
        return _betas.length;
    }

    private void powerScores(final double[] regressors_, final double[] workspace_)
    {
        Arrays.fill(workspace_, 0.0);

        for (int i = 0; i < _params.getEntryCount(); i++)
        {
            addEntryPowerScores(regressors_, i, workspace_);
        }
    }

    private void rawPowerScores(final double[] entryWeights_, final double[] workspace_)
    {
        final int inputSize = _regWorkspace.length;
        final int outputSize = _betas.length;

        for (int i = 0; i < outputSize; i++)
        {
            double sum = 0.0;
            final double[] betaArray = _betas[i];

            for (int k = 0; k < inputSize; k++)
            {
                sum += entryWeights_[k] * betaArray[k];
            }

            workspace_[i] = sum;
        }
    }

    private void multiLogisticFunction(final double[] regressors_, final double[] workspace_)
    {
        powerScores(regressors_, workspace_);
        MultiLogistic.multiLogisticFunction(workspace_, workspace_);
    }

    /**
     * Note that this method is synchronized. Therefore, it may be called from
     * any thread, and you are guaranteed to get a viable model within that
     * thread.
     *
     * @return A clone of this model
     */
    @Override
    public final synchronized ItemModel<S, R, T> clone()
    {
        //Yes, yes, this is bad form. However, this class is final and I don't feel like
        //making all its internal variables not-final so that I can use the proper clone
        //idiom.

        // N.B: It's OK for these models to share the packed params, since these are never modified or given out
        // (were cloned in the initial construction), so _packed is effectively immutable here.
        return new ItemModel<>(this._params, this._packed);
    }
}
