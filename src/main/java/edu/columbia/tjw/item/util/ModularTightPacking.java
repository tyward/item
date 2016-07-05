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

import java.util.Arrays;

/**
 * This class is designed to tightly pack the given input array using modular
 * arithmetic.
 *
 * This should allow us to pack the given ints into a small array (not much
 * larger than input_.length), while having the property that each int can be
 * looked up efficiently.
 *
 * Given some set of integers i, can I create a small array such that a simple
 * operation (e.g. mod and addition) can produce a unique index into that array
 * for every integer in the input. I need not be able to detect the usage of
 * integers not in the input. I need not use every element of the smaller array
 * either.
 *
 * @author tyler
 */
public class ModularTightPacking
{
    private final int _modulus;
    private final int _min;

    public ModularTightPacking(final int[] input_)
    {
        final int[] workspace = input_.clone();

        Arrays.sort(workspace);

        final int arraySize = workspace.length;
        int min = workspace[0];
        int max = workspace[0];

        for (int i = 1; i < arraySize; i++)
        {
            if (workspace[i - 1] == workspace[i])
            {
                throw new IllegalArgumentException("Inputs must be unique.");
            }

            min = Math.min(workspace[i], min);
            max = Math.max(workspace[i], max);
        }

        _min = min;
        final int maxSize = max - min;
        int modulus = 0;

        for (int i = 0; i < arraySize; i++)
        {
            workspace[i] = workspace[i] - min;
        }

        for (int i = input_.length; i < maxSize; i++)
        {
            if (testPacking(i, workspace))
            {
                modulus = i;
                break;
            }
        }

        if (0 == modulus)
        {
            throw new IllegalArgumentException("Impossible.");
        }

        _modulus = modulus;
    }

    private static boolean testPacking(final int modulus_, final int[] workspace_)
    {
        final boolean[] testArray = new boolean[modulus_];

        for (int i = 0; i < workspace_.length; i++)
        {
            final int testIndex = workspace_[i] % modulus_;

            if (testArray[testIndex])
            {
                return false;
            }

            testArray[testIndex] = true;
        }

        return true;
    }

    public int getArraySize()
    {
        return _modulus;
    }

    public int computeIndex(final int input_)
    {
        final int output = (input_ - _min) % _modulus;
        return output;
    }

}
