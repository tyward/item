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
package edu.columbia.tjw.tools;

import edu.columbia.tjw.item.util.LogUtil;
import edu.columbia.tjw.item.util.random.RandomTool;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.math3.distribution.NormalDistribution;

/**
 *
 * This class uses the gaussian cluster algorithm to generate a highly complex
 * dataset.
 *
 * Essentially, this data would be a fractal if the algorithm was carried
 * through arbitrarily many steps. The goal of this generator is to produce a
 * dataset that will not be simple, or easy for a model to properly understand.
 * It may also be used to generate in and out of sample sets, etc...
 *
 * One of the primary advantages of this approach is that the exact probability
 * of every point is known exactly. So (for instance), the entropy of the
 * dataset can be computed exactly.
 *
 * @author tyler
 */
public class GaussianClusterGenerator
{
    private static final Logger LOG = LogUtil.getLogger(GaussianClusterGenerator.class);

    private final double[][] _workspace;
    private final double[] _weights;
    private final double[][] _samples;
    private final int _dimension;
    private final int _sampleSize;
    private final int _testSize;
    private final int _level;
    private final double _sigma;
    private final Random _rand;
    private final NormalDistribution _dist;

    public static void main(final String[] args_)
    {
        try
        {
            final Random rand = RandomTool.getRandom();
            final int dimension = 2;
            final int baseSize = 5;
            final int sampleScale = 4;
            final int testSize = 10;
            final double sigma = 0.01;
            final GaussianClusterGenerator gen = buildGenerator(1, dimension, baseSize, sampleScale, testSize, sigma, rand);

            final double[] workspace = new double[dimension];

            LOG.info("Extracting sample points.");
            System.out.println("x,y");

            for (int i = 0; i < gen.getSampleSize(); i++)
            {
                gen.getSamplePoint(i, workspace);
                System.out.println(workspace[0] + "," + workspace[1]);
            }

            LOG.info("Extracting generated points.");
            System.out.println("x,y,weight");

            for (int i = 0; i < 100; i++)
            {
                final double weight = gen.generatePoint(workspace);

                System.out.println(workspace[0] + "," + workspace[1] + "," + weight);
            }

            LOG.info("Done.");

        }
        catch (final Exception e)
        {
            LOG.log(Level.WARNING, "Exception in main", e);
        }

    }

    public static GaussianClusterGenerator buildGenerator(final int level_, final int dimension_, final int baseSize_,
            final int sampleScale_, final int testSize_, final double sigma_, final Random rand_)
    {

        int size = baseSize_;
        GaussianClusterGenerator gen = new GaussianClusterGenerator(dimension_, size, testSize_, sigma_, rand_);

        for (int i = 1; i < level_; i++)
        {
            size *= sampleScale_;
            gen = new GaussianClusterGenerator(gen, rand_, size);
        }

        return gen;
    }

    private GaussianClusterGenerator(final GaussianClusterGenerator underlying_, final Random rand_, final int sampleSize_)
    {
        _level = 1 + underlying_.getLevel();
        _testSize = underlying_.getTestSize();
        _dimension = underlying_.getDimension();
        _sampleSize = sampleSize_;
        _sigma = underlying_.getSigma();
        _rand = rand_;
        _dist = new NormalDistribution(0.0, _sigma);

        _samples = new double[_sampleSize][_dimension];
        _workspace = new double[_testSize][_dimension];
        _weights = new double[_testSize];

        for (int i = 0; i < _samples.length; i++)
        {
            underlying_.generatePoint(_samples[i]);
        }
    }

    private GaussianClusterGenerator(final int dimension_, final int sampleSize_, final int testSize_, final double sigma_, final Random rand_)
    {
        _level = 1;
        _testSize = testSize_;
        _dimension = dimension_;
        _sampleSize = sampleSize_;
        _sigma = sigma_;
        _rand = rand_;
        _dist = new NormalDistribution(0.0, _sigma);

        _samples = new double[_sampleSize][_dimension];
        _workspace = new double[_testSize][_dimension];
        _weights = new double[_testSize];

        for (int i = 0; i < _samples.length; i++)
        {
            fillUniform(_samples[i]);
        }
    }

    public void getSamplePoint(final int index_, final double[] output_)
    {
        System.arraycopy(_samples[index_], 0, output_, 0, _dimension);
    }

    public double generatePoint(final double[] output_)
    {
        double weightSum = 0.0;

        //First, generate test point. 
        for (int i = 0; i < _testSize; i++)
        {
            fillUniform(_workspace[i]);
            final double weight = calculateWeight(_workspace[i]);
            weightSum += weight;
            _weights[i] = weight;
        }

        final double randVal = _rand.nextDouble();

        double scaledSum = 0.0;

        for (int i = 0; i < _testSize; i++)
        {
            final double weight = _weights[i];
            final double scaledWeight = weight / weightSum;
            scaledSum += scaledWeight;

            if (scaledSum >= randVal)
            {
                System.arraycopy(_workspace[i], 0, output_, 0, _dimension);
                return weight;
            }
        }

        throw new IllegalStateException("Impossible.");
    }

    public int getDimension()
    {
        return _dimension;
    }

    public int getSampleSize()
    {
        return _sampleSize;
    }

    public int getTestSize()
    {
        return _testSize;
    }

    public int getLevel()
    {
        return _level;
    }

    public double getSigma()
    {
        return _sigma;
    }

    private void fillUniform(final double[] array_)
    {
        if (array_.length != _dimension)
        {
            throw new IllegalArgumentException("Invalid array.");
        }

        for (int w = 0; w < _dimension; w++)
        {
            array_[w] = _rand.nextDouble();
        }
    }

    private double calculateWeight(final double[] point_)
    {
        if (point_.length != _dimension)
        {
            throw new IllegalArgumentException("Invalid array.");
        }

        double weightSum = 0.0;

        for (int i = 0; i < _samples.length; i++)
        {
            final double[] sample = _samples[i];

            double r2 = 0.0;

            for (int w = 0; w < _dimension; w++)
            {
                final double diff = sample[w] - point_[w];
                final double d2 = diff * diff;
                r2 += d2;
            }

            final double radius = Math.sqrt(r2);
            final double density = _dist.density(radius);
            weightSum += density;
        }

        return weightSum;
    }

}
