/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.columbia.tjw.item.fit.curve;

import edu.columbia.tjw.item.ItemCurve;
import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemFittingGrid;
import edu.columbia.tjw.item.ItemModel;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.util.RectangularDoubleArray;
import edu.columbia.tjw.item.util.LogLikelihood;
import edu.columbia.tjw.item.util.MultiLogistic;
import edu.columbia.tjw.item.ItemWorkspace;
import edu.columbia.tjw.item.optimize.EvaluationResult;
import edu.columbia.tjw.item.optimize.MultivariateDifferentiableFunction;
import edu.columbia.tjw.item.optimize.MultivariateGradient;
import edu.columbia.tjw.item.optimize.MultivariatePoint;
import edu.columbia.tjw.item.optimize.ThreadedMultivariateFunction;
import java.util.List;

/**
 *
 * @author tyler
 * @param <S>
 * @param <R>
 * @param <T>
 */
public class CurveOptimizerFunction<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
        extends ThreadedMultivariateFunction implements MultivariateDifferentiableFunction
{
    private final LogLikelihood<S> _likelihood;
    private final ParamGenerator<S, R, T> _generator;
    private final R _field;
    private final double[] _regressor;
    private final int _size;
    private final double[] _workspace;
    private final int _toIndex;
    private final double _mean;
    private final double _stdDev;

    private final ItemModel<S, R, T> _model;
    private final RectangularDoubleArray _powerScores;
    private final RectangularDoubleArray _actualProbabilities;

    private ItemCurve<?> _trans;
    private double _interceptAdjustment;
    private double _beta;
    private MultivariatePoint _prevPoint;
    //private final MultivariateDifferentiableFunction _diff;

    public CurveOptimizerFunction(final ParamGenerator<S, R, T> generator_, final R field_, final S fromStatus_, final S toStatus_,
            final RectangularDoubleArray powerScores_, final RectangularDoubleArray actualProbabilities_, final ItemFittingGrid<S, R> grid_,
            final ItemModel<S, R, T> model_, final int[] indexList_)
    {
        super(10 * 1000);

        _likelihood = new LogLikelihood<>(fromStatus_.getFamily());
        _powerScores = powerScores_;
        _actualProbabilities = actualProbabilities_;
        _model = model_;
        _generator = generator_;
        _field = field_;
        _size = _powerScores.getRows();
        _workspace = new double[_generator.paramCount()];
        _regressor = new double[_size];
        _toIndex = fromStatus_.getReachable().indexOf(toStatus_);

        //final NumericGridReader<?> reader = grid_.getReader(_field);
        //Take one random point as the mean for our optimization.
        //final int meanSelector = (int) (_size * RandomTool.nextDouble());
        //final double mean = reader.asDouble(indexList_[meanSelector], offsetList_[meanSelector]);
        double eX = 0.0;
        double eX2 = 0.0;

        for (int i = 0; i < _size; i++)
        {
            final int mapped = indexList_[i];
            final double regressor = grid_.getRawRegressor(mapped, _field);

            _regressor[i] = regressor;

            eX += regressor;
            eX2 += (regressor * regressor);
        }

        eX = eX / _size;
        eX2 = eX2 / _size;

        _mean = eX;
        _stdDev = Math.sqrt(eX2 - (eX * eX));
        //_diff = new MultivariateFiniteDiffDerivFunction(this, new BasicAdaptiveComparator<MultivariatePoint, MultivariateFunction>(100000, 3.0));
    }

    public double getMean()
    {
        return _mean;
    }

    public double getStdDev()
    {
        return _stdDev;
    }

    @Override
    public int dimension()
    {
        return _generator.paramCount();
    }

    @Override
    public int numRows()
    {
        return _size;
    }

    @Override
    protected void prepare(MultivariatePoint input_)
    {
        if (null != _prevPoint)
        {
            if (_prevPoint.equals(input_))
            {
                return;
            }
        }

        _prevPoint = input_.clone();

        for (int i = 0; i < input_.getDimension(); i++)
        {
            _workspace[i] = input_.getElement(i);
        }

        _trans = _generator.generateTransformation(_workspace);
        _interceptAdjustment = _generator.getInterceptAdjustment(_workspace);
        _beta = _generator.getBeta(_workspace);
        //System.out.println("Prepared: " + input_);
    }

    @Override
    protected void evaluate(int start_, int end_, EvaluationResult result_)
    {
        if (start_ == end_)
        {
            return;
        }

        final int cols = _powerScores.getColumns();

        final double[] computed = new double[cols];
        final double[] actual = new double[cols];

        for (int i = start_; i < end_; i++)
        {
            for (int k = 0; k < cols; k++)
            {
                computed[k] = _powerScores.get(i, k);
                actual[k] = _actualProbabilities.get(i, k);
            }

            final double regressor = _regressor[i];
            final double transformed = _trans.transform(regressor);
            final double contribution = (_interceptAdjustment + (_beta * transformed));

            computed[_toIndex] += contribution;

            //Converte these power scores into probabilities.
            MultiLogistic.multiLogisticFunction(computed, computed);

            final double logLikelihood = _likelihood.logLikelihood(_model.getParams().getStatus(), actual, computed);

            result_.add(logLikelihood, result_.getHighWater(), i + 1);
        }
    }

    @Override
    public MultivariateGradient calculateDerivative(MultivariatePoint input_, EvaluationResult result_, double precision_)
    {

        this.prepare(input_);

        final int start = 0;
        final int end = this.numRows();
        final int dimension = input_.getDimension();

        final double[] derivative = new double[dimension];
        final ItemWorkspace<S> workspace = _model.generateWorkspace();

        final List<S> reachable = _model.getParams().getStatus().getReachable();
        int count = 0;
        final int reachableCount = reachable.size();

        final double[] scores = new double[reachableCount];

        //We are only interested in the specific element being curved....
        //Therefore, set the beta to 1.0, the result is a multiple of beta
        //for the special case where only one beta is set. We will scale afterwards. 
        final double[] betas = new double[reachableCount];
        betas[this._toIndex] = 1.0;

        final double[] workspace1 = new double[reachableCount];
        final double[] output = new double[reachableCount];
        final double[] actual = new double[reachableCount];

        final int interceptIndex = _generator.getInterceptParamNumber();
        final int betaIndex = _generator.getBetaParamNumber();

        for (int i = start; i < end; i++)
        {
            for (int w = 0; w < reachableCount; w++)
            {
                scores[w] = _powerScores.get(i, w);
                actual[w] = _actualProbabilities.get(i, w);
            }

            //After this, workspace1 holds the model probabilities, output holds the xDerivatives of the probabilities.
            MultiLogistic.multiLogisticRegressorDerivatives(scores, betas, this._toIndex, workspace1, output);
            MultiLogistic.multiLogisticFunction(scores, workspace1);

            double xDerivative = 0.0;

            for (int w = 0; w < reachableCount; w++)
            {
                final double probRatio = actual[w] / workspace1[w];
                final double derivTerm = output[w] * probRatio;
                xDerivative += derivTerm;
            }

            final double regressor = this._regressor[i];

            final double transformed = this._trans.transform(regressor);

            //In our special case, the derivative is directly proportional to beta, because we apply it to only one state. 
            final double interceptDerivative = xDerivative;

            final double betaDerivative = xDerivative * transformed;

            derivative[interceptIndex] += interceptDerivative;
            derivative[betaIndex] += betaDerivative;

            for (int w = 0; w < _trans.getCurveType().getParamCount(); w++)
            {
                final int mapped = _generator.translateParamNumber(w);
                final double paramDerivative = _trans.derivative(w, regressor);
                final double combined = xDerivative * this._beta * paramDerivative;
                derivative[mapped] += combined;
            }

            count++;
        }

        //N.B: we are computing the negative log likelihood. 
        final double invCount = -1.0 / count;

        for (int i = 0; i < derivative.length; i++)
        {
            derivative[i] = derivative[i] * invCount;
        }

        final MultivariatePoint der = new MultivariatePoint(derivative);

        //final MultivariateGradient grad = _diff.calculateDerivative(input_, result_, precision_);
        final MultivariateGradient grad = new MultivariateGradient(input_, der, null, 0.0);
        return grad;
    }

    @Override
    public int resultSize(int start_, int end_)
    {
        final int size = (end_ - start_);
        return size;
    }

}
