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
package edu.columbia.tjw.item.visualize;

import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemModel;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemRegressorReader;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.data.InterpolatedCurve;
import edu.columbia.tjw.item.data.ItemGrid;
import edu.columbia.tjw.item.fit.ItemCalcGrid;
import edu.columbia.tjw.item.util.EnumFamily;
import java.util.List;
import java.util.Map;

/**
 *
 * @author tyler
 * @param <S> The status family for this visualizer
 * @param <R> The regressor family for this visualizer
 * @param <T> The curve family for this visualizer
 */
public class ModelVisualizer<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
{
    private final ItemModel<S, R, T> _model;

//    public static void main(final String[] args_)
//    {
//        try
//        {
//            if(args_.length != 1)
//            {
//                System.out.println("Usage: ModelVisualizer <modelParameterFile>");
//            }
//
//            final File paramFile = new File(args_[0]);
//            
//            final ObjectInputStream oIn = new ObjectInputStream(new FileInputStream(paramFile));
//            final ItemParameters<?, ?, ?> params = (ItemParameters<?, ?, ?>) oIn.readObject();
//            
//            
//            
//            
//
//        }
//        catch (final Exception e)
//        {
//            e.printStackTrace();
//        }
//    }
    public ModelVisualizer(final ItemModel<S, R, T> model_)
    {
        _model = model_;
    }

    /**
     * Holding all other regressors fixed, we compute the probability of the
     * given transition for various values of this target regressor.
     *
     * The result is two arrays, the regressor values and transition
     * probabilities.
     *
     * @param to_ The status transition to target
     * @param regressor_ The regressor to target
     * @param regValues_ Values to use for all regressors (except possibly
     * regressor_)
     * @param regMin_ The starting value for regressor_
     * @param regMax_ The ending value for regressor_
     * @param steps_ The number of steps to use
     * @return An interpolated curve over regressor_ on [regMin_, regMax_] of
     * transition probabilities for an element with regValues_
     */
    public InterpolatedCurve graph(final S to_, final R regressor_, final Map<R, Double> regValues_, final double regMin_, final double regMax_, final int steps_)
    {
        if (regMin_ >= regMax_)
        {
            throw new IllegalArgumentException("Reg min must be greater than reg max.");
        }
        if (steps_ <= 0)
        {
            throw new IllegalArgumentException("Steps must be positive: " + steps_);
        }

        final double stepSize = (regMax_ - regMin_) / steps_;

        final InnerGrid grid = new InnerGrid(steps_, regMin_, stepSize, regressor_, regValues_);
        final ItemCalcGrid<S, R, T> paramGrid = new ItemCalcGrid<>(_model.getParams(), grid);

        final List<S> reachable = _model.getParams().getStatus().getReachable();
        final int toIndex = reachable.indexOf(to_);

        if (toIndex < 0)
        {
            throw new IllegalArgumentException("Status not reachable: " + to_);
        }

        final double[] probability = new double[reachable.size()];

        final double[] x = new double[steps_];
        final double[] y = new double[steps_];

        for (int i = 0; i < steps_; i++)
        {
            _model.transitionProbability(paramGrid, i, probability);

            x[i] = regMin_ + (i * stepSize);
            y[i] = probability[toIndex];
        }

        final InterpolatedCurve output = new InterpolatedCurve(x, y, true, false);
        return output;
    }

    private final class InnerGrid implements ItemGrid<R>
    {
        private final int _steps;
        private final R _regressor;
        private final ItemRegressorReader[] _readers;

        public InnerGrid(final int steps_, final double minValue_, final double stepSize_, final R regressor_, final Map<R, Double> regValues_)
        {
            _steps = steps_;
            _regressor = regressor_;

            _readers = new ItemRegressorReader[regressor_.getFamily().size()];

            for (final Map.Entry<R, Double> entry : regValues_.entrySet())
            {
                final R next = entry.getKey();
                final Double value = entry.getValue();
                _readers[next.ordinal()] = new ConstantRegressorReader(_steps, value);
            }

            _readers[regressor_.ordinal()] = new SteppedRegressorReader(_steps, minValue_, stepSize_);
        }

        @Override
        public int size()
        {
            return _steps;
        }

        @Override
        public ItemRegressorReader getRegressorReader(R field_)
        {
            return _readers[field_.ordinal()];
        }

        @Override
        public EnumFamily<R> getRegressorFamily()
        {
            return _regressor.getFamily();
        }

    }

    private static final class ConstantRegressorReader implements ItemRegressorReader
    {
        private final int _size;
        private final double _regValue;

        public ConstantRegressorReader(final int size_, final double regValue_)
        {
            _regValue = regValue_;
            _size = size_;

        }

        @Override
        public double asDouble(int index_)
        {
            return _regValue;
        }

        @Override
        public int size()
        {
            return _size;
        }

    }

    private static final class SteppedRegressorReader implements ItemRegressorReader
    {
        private final int _size;
        private final double _startValue;
        private final double _stepValue;

        public SteppedRegressorReader(final int size_, final double startValue_, final double stepValue_)
        {
            _startValue = startValue_;
            _stepValue = stepValue_;
            _size = size_;
        }

        @Override
        public double asDouble(int index_)
        {
            final double reg = _startValue + (index_ * _stepValue);
            return reg;
        }

        @Override
        public int size()
        {
            return _size;
        }

    }

}
