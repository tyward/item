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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 *
 * @author tyler
 */
public final class CompileDescriptor
{
    private static final Option FROM_STATUS_COL = new Option("from_status_col", true,
            "(Optional) Name of the from status column (for data sets that contain mixed from-status entries).");
    private static final Option TO_STATUS_COL = new Option("to_status_col", true,
            "Name of the to status column (commonly called 'label' in classification problems).");
    private static final Option NUMERIC_COLUMNS = new Option("numeric_columns", true,
            "(Optional) Comma separated list of columns holding numeric (i.e. floating point) values");
    private static final Option BOOLEAN_COLUMNS = new Option("boolean_columns", true,
            "(Optional) Comma separated list of columns holding boolean (i.e. TRUE/FALSE) values");
    private static final Option ENUM_COLUMNS = new Option("enum_columns", true,
            "(Optional) Comma separated list of columns holding enum (i.e. strings from a limited set: {A, B, C}) values");
    private static final Option INPUT_FILE = new Option("input_file", true,
            "The CSV data used to generate the descriptors.");
    private static final Option OUTPUT_FILE = new Option("output_file", true,
            "The file to which the descriptor is to be written");

    public CompileDescriptor()
    {
    }

    public static void main(final String[] args_)
    {
        FROM_STATUS_COL.setRequired(false);
        TO_STATUS_COL.setRequired(true);
        NUMERIC_COLUMNS.setRequired(false);
        BOOLEAN_COLUMNS.setRequired(false);
        ENUM_COLUMNS.setRequired(false);
        INPUT_FILE.setRequired(true);
        OUTPUT_FILE.setRequired(true);

        final Options opt = new Options();
        opt.addOption(FROM_STATUS_COL);
        opt.addOption(TO_STATUS_COL);
        opt.addOption(NUMERIC_COLUMNS);
        opt.addOption(BOOLEAN_COLUMNS);
        opt.addOption(ENUM_COLUMNS);
        opt.addOption(INPUT_FILE);
        opt.addOption(OUTPUT_FILE);

        try
        {
            final CommandLineParser parser = new DefaultParser();
            final CommandLine cmd = parser.parse(opt, args_);

            final String fromStatCol = cmd.getOptionValue(FROM_STATUS_COL.getOpt(), null);
            final String toStatCol = cmd.getOptionValue(TO_STATUS_COL.getOpt());

            final SortedSet<String> numeric = getStringSet(cmd, NUMERIC_COLUMNS);
            final SortedSet<String> boolCols = getStringSet(cmd, BOOLEAN_COLUMNS);
            final SortedSet<String> enumCols = getStringSet(cmd, ENUM_COLUMNS);

            final String outputFile = cmd.getOptionValue(OUTPUT_FILE.getOpt());
            final String inputFile = cmd.getOptionValue(INPUT_FILE.getOpt());

            final File input = new File(inputFile);
            final File output = new File(outputFile);

            if (!input.exists())
            {
                throw new FileNotFoundException("Input file does not exist: " + input.getCanonicalPath());
            }
            if (output.exists())
            {
                throw new IOException("Output file already exists, refusing to overwrite: " + input.getCanonicalPath());
            }

            final ColumnDescriptorSet desc = new ColumnDescriptorSet(fromStatCol, toStatCol, numeric, boolCols, enumCols);

            final CsvLoader loader = new CsvLoader(input, desc);

            final CompiledDataDescriptor compiled = loader.getCompiledDescriptor();

            try (final FileOutputStream fOut = new FileOutputStream(output);
                    final ObjectOutputStream oOut = new ObjectOutputStream(fOut))
            {
                oOut.writeObject(compiled);
            }

        }
        catch (final ParseException e)
        {
            System.err.println(e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("CompileDescriptor", opt);
        }
        catch (final IOException e)
        {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }

    }

    private static SortedSet<String> getStringSet(final CommandLine cmd_, final Option opt_)
    {
        final String setString = cmd_.getOptionValue(opt_.getOpt(), null);

        if (null == setString)
        {
            return Collections.emptySortedSet();
        }

        final String[] cols = setString.split(",");
        final SortedSet<String> output = new TreeSet<>(Arrays.asList(cols));
        return Collections.unmodifiableSortedSet(output);
    }

}
