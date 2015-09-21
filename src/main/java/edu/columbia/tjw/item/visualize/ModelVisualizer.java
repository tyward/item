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

import edu.columbia.tjw.item.ItemCurve;
import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemModel;
import edu.columbia.tjw.item.ItemParameters;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemRegressorReader;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.data.ItemGrid;
import edu.columbia.tjw.item.fit.ItemCalcGrid;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 *
 * @author tyler
 * @param <S>
 * @param <R>
 * @param <T>
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
     * @param to_
     * @param regressor_
     * @param regValues_
     * @param regMin_
     * @param regMax_
     * @param steps_
     * @return
     */
    public double[][] graph(final S to_, final R regressor_, final Map<R, Double> regValues_, final double regMin_, final double regMax_, final int steps_)
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

        final InnerGrid grid = new InnerGrid(steps_, regMin_, stepSize, regressor_, regValues_, _model.getParams());
        final ItemCalcGrid<S, R, T> paramGrid = new ItemCalcGrid<>(_model.getParams(), grid);

        final List<S> reachable = _model.getParams().getStatus().getReachable();
        final int toIndex = reachable.indexOf(to_);

        if (toIndex < 0)
        {
            throw new IllegalArgumentException("Status not reachable: " + to_);
        }

        final double[] probability = new double[reachable.size()];

        final double[][] output = new double[2][steps_];

        for (int i = 0; i < steps_; i++)
        {
            _model.transitionProbability(paramGrid, i, probability);

            final double x = regMin_ + (i * stepSize);
            final double y = probability[toIndex];

            output[0][i] = x;
            output[1][i] = y;

        }

        return output;
    }

    private final class InnerGrid implements ItemGrid<R>
    {
        private final int _steps;
        private final ItemParameters<S, R, T> _params;
        private final R _regressor;
        private final double[] _regValues;
        private final double _minValue;
        private final double _stepSize;
        private final int[] _targetIndices;

        public InnerGrid(final int steps_, final double minValue_, final double stepSize_, final R regressor_, final Map<R, Double> regValues_, final ItemParameters<S, R, T> params_)
        {
            _steps = steps_;
            _params = params_;
            _regressor = regressor_;
            _minValue = minValue_;
            _stepSize = stepSize_;

            final int regCount = _params.regressorCount();

            _regValues = new double[regCount];
            final int[] indices = new int[regCount];
            int pointer = 0;

            final List<R> regList = _params.getRegressorList();

            for (int i = 0; i < regCount; i++)
            {
                final R next = regList.get(i);

                if (regressor_.equals(next))
                {
                    _regValues[i] = minValue_;
                    indices[pointer++] = i;
                }
                else
                {
                    _regValues[i] = regValues_.get(next);
                }
            }

            _targetIndices = Arrays.copyOf(indices, pointer);

            for (int i = 0; i < regCount; i++)
            {
                final ItemCurve<?> curve = params_.getTransformation(i);

                if (null == curve)
                {
                    continue;
                }

                _regValues[i] = curve.transform(_regValues[i]);
            }
        }

        @Override
        public int size()
        {
            return _steps;
        }

        @Override
        public ItemRegressorReader getRegressorReader(R field_)
        {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

    }

}
