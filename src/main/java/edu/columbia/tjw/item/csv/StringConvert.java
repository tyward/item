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
package edu.columbia.tjw.item.csv;

/**
 *
 * @author tyler
 */
public final class StringConvert
{
    private StringConvert()
    {
    }

    public static double convertDouble(final String input_)
    {
        try
        {
            final double converted = Double.parseDouble(input_);
            return converted;
        }
        catch (final NumberFormatException e)
        {
            return Double.NaN;
        }
    }

    public static boolean convertBoolean(final String input_)
    {
        final boolean converted = Boolean.parseBoolean(input_);

        if (converted)
        {
            return true;
        }

        if (input_.equalsIgnoreCase("t"))
        {
            //Sometimes this is used as the truth value...
            return true;
        }

        return false;
    }

}
