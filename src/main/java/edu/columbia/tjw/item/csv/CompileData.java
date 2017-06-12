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

import edu.columbia.tjw.item.base.SimpleRegressor;
import edu.columbia.tjw.item.base.SimpleStatus;
import edu.columbia.tjw.item.data.ItemStatusGrid;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
public final class CompileData
{
    private static final Option DESCRIPTOR_FILE = new Option("descriptor_file", true,
            "The file containing the data descriptor previously compiled");
    private static final Option INPUT_FILE = new Option("input_file", true,
            "The CSV data to be processed");
    private static final Option OUTPUT_FILE = new Option("output_file", true,
            "The output to hold the binary processed data");
    private static final Option START_STATUS = new Option("start_status", true,
            "(Optional) If specified, then only rows starting in the given status will be considered, the descriptor must have been compiled with a from_status_col "
            + "(still experimental, partition the data manually if this causes problems)");

    private CompileData()
    {
    }

    public static void main(final String[] args_)
    {
        DESCRIPTOR_FILE.setRequired(true);
        INPUT_FILE.setRequired(true);
        OUTPUT_FILE.setRequired(true);
        OUTPUT_FILE.setRequired(false);

        final Options opt = new Options();
        opt.addOption(DESCRIPTOR_FILE);
        opt.addOption(INPUT_FILE);
        opt.addOption(OUTPUT_FILE);
        opt.addOption(START_STATUS);

        try
        {
            final CommandLineParser parser = new DefaultParser();
            final CommandLine cmd = parser.parse(opt, args_);

            final File descriptor = parseFile(cmd, DESCRIPTOR_FILE, true);
            final File input = parseFile(cmd, INPUT_FILE, true);
            final File output = parseFile(cmd, OUTPUT_FILE, false);
            final String startStatusName = cmd.getOptionValue(START_STATUS.getOpt(), null);

            final CompiledDataDescriptor compiled = getCompiled(descriptor);
            final CsvLoader loader = new CsvLoader(input, compiled.getColDescriptorSet());

            final SimpleStatus startStatus;

            if (null != startStatusName)
            {
                startStatus = compiled.getStatusFamily().getFromName(startStatusName);
            }
            else
            {
                startStatus = null;
            }

            final ItemStatusGrid<SimpleStatus, SimpleRegressor> grid = loader.generateGrid(compiled, startStatus);

            try (final FileOutputStream fOut = new FileOutputStream(output);
                    final ObjectOutputStream oOut = new ObjectOutputStream(fOut))
            {
                oOut.writeObject(grid);
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

    private static CompiledDataDescriptor getCompiled(final File inputFile_) throws IOException
    {
        try (final FileInputStream fin = new FileInputStream(inputFile_);
                final ObjectInputStream oin = new ObjectInputStream(fin))
        {
            final CompiledDataDescriptor desc = (CompiledDataDescriptor) oin.readObject();
            return desc;
        }
        catch (final ClassNotFoundException e)
        {
            throw new IOException(e);
        }
    }

    private static File parseFile(final CommandLine cmd_, final Option opt_, final boolean expectExist_) throws IOException
    {
        final String fileName = cmd_.getOptionValue(opt_.getOpt());
        final File converted = new File(fileName);
        final boolean exists = converted.exists();

        if (exists == expectExist_)
        {
            return converted;
        }

        if (expectExist_)
        {
            throw new FileNotFoundException("File does not exist: " + converted.getCanonicalPath());
        }
        else
        {
            throw new IOException("File already exists, will not overwrite: " + converted.getCanonicalPath());
        }
    }

}
