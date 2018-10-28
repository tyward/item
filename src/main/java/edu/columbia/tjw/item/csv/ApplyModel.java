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

import edu.columbia.tjw.item.ItemModel;
import edu.columbia.tjw.item.ItemParameters;
import edu.columbia.tjw.item.ItemRegressorReader;
import edu.columbia.tjw.item.base.SimpleRegressor;
import edu.columbia.tjw.item.base.SimpleStatus;
import edu.columbia.tjw.item.base.StandardCurveType;
import edu.columbia.tjw.item.data.ItemStatusGrid;
import edu.columbia.tjw.item.fit.ItemCalcGrid;
import edu.columbia.tjw.item.fit.ItemParamGrid;
import edu.columbia.tjw.item.util.EnumFamily;
import edu.columbia.tjw.item.util.LogUtil;
import org.apache.commons.cli.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.*;
import java.util.logging.Logger;

/**
 * @author tyler
 */
public final class ApplyModel
{
    private static final Logger LOG = LogUtil.getLogger(ApplyModel.class);

    private static final Option DESCRIPTOR_FILE = new Option("descriptor_file", true,
            "The file containing the data descriptor previously compiled");
    private static final Option COMPILED_DATA = new Option("compiled_data", true,
            "The compiled data to be processed");
    private static final Option MODEL = new Option("model", true,
            "The model to apply to the data.");
    private static final Option OUTPUT_FILE = new Option("output_file", true,
            "The file where output (csv data) is written.");

    private ApplyModel()
    {
    }

    public static void main(final String[] args_)
    {
        DESCRIPTOR_FILE.setRequired(true);
        COMPILED_DATA.setRequired(true);
        MODEL.setRequired(true);
        OUTPUT_FILE.setRequired(true);

        final Options opt = new Options();
        opt.addOption(DESCRIPTOR_FILE);
        opt.addOption(COMPILED_DATA);
        opt.addOption(MODEL);
        opt.addOption(OUTPUT_FILE);

        try
        {
            final CommandLineParser parser = new DefaultParser();
            final CommandLine cmd = parser.parse(opt, args_);

            final File descriptor = generateFile(cmd, DESCRIPTOR_FILE, true);
            final File input_file = generateFile(cmd, COMPILED_DATA, true);
            final File model_file = generateFile(cmd, MODEL, true);
            final File output_file = generateFile(cmd, OUTPUT_FILE, false);

            final CompiledDataDescriptor compiled = getCompiled(descriptor);
            final ItemStatusGrid<SimpleStatus, SimpleRegressor> grid = loadGrid(input_file);
            final ItemParameters<SimpleStatus, SimpleRegressor, StandardCurveType> params = loadParams(model_file);

            final ItemModel<SimpleStatus, SimpleRegressor, StandardCurveType> model = new ItemModel<>(params);

            final int reachableCount = params.getStatus().getReachableCount();
            final int size = grid.size();

            final double[][] predictions = new double[reachableCount][size];
            final ItemParamGrid<SimpleStatus, SimpleRegressor, StandardCurveType> paramGrid =
                    new ItemCalcGrid<>(params, grid);

            LOG.info("Running calculations.");
            final double[] workspace = new double[reachableCount];

            for (int i = 0; i < size; i++)
            {
                model.transitionProbability(paramGrid, i, workspace);

                for (int w = 0; w < reachableCount; w++)
                {
                    predictions[w][i] = workspace[w];
                }
            }

            LOG.info("Completed calculations, writing output.");
            final EnumFamily<SimpleRegressor> regFamily = grid.getRegressorFamily();
            final int regCount = regFamily.size();
            final String[] colNames = new String[regCount + reachableCount];

            for (int i = 0; i < regCount; i++)
            {
                colNames[i] = regFamily.getFromOrdinal(i).name();
            }

            for (int i = 0; i < reachableCount; i++)
            {
                colNames[regCount + i] = "Prediction[" + params.getStatus().getReachable().get(i).name() + "]";
            }

            try (final FileOutputStream out = new FileOutputStream(output_file))
            {
                CSVPrinter printer = CSVFormat.DEFAULT.withRecordSeparator("\n").print(new PrintWriter(out));
                printer.printRecord((Object[]) colNames);

                final String[] data = new String[colNames.length];

                for (int i = 0; i < size; i++)
                {

                    for (int z = 0; z < regCount; z++)
                    {
                        final SimpleRegressor reg = regFamily.getFromOrdinal(z);
                        final ItemRegressorReader reader = grid.getRegressorReader(reg);

                        if (null == reader)
                        {
                            data[z] = "null";
                        }
                        else
                        {
                            data[z] = Double.toString(reader.asDouble(i));
                        }
                    }

                    for (int z = 0; z < reachableCount; z++)
                    {
                        data[regCount + z] = Double.toString(predictions[z][i]);
                    }

                    printer.printRecord((Object[]) data);
                }

                printer.flush();
                printer.close();
            }

            LOG.info("Done");
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

    private static ItemParameters<SimpleStatus, SimpleRegressor, StandardCurveType> loadParams(final File inputFile_) throws IOException
    {
        if (null == inputFile_)
        {
            return null;
        }

        try (final FileInputStream fin = new FileInputStream(inputFile_);
             final ObjectInputStream oin = new ObjectInputStream(fin))
        {
            final ItemParameters<SimpleStatus, SimpleRegressor, StandardCurveType> model
                    = (ItemParameters<SimpleStatus, SimpleRegressor, StandardCurveType>) oin.readObject();
            return model;
        }
        catch (final ClassNotFoundException e)
        {
            throw new IOException(e);
        }
    }

    private static ItemStatusGrid<SimpleStatus, SimpleRegressor> loadGrid(final File inputFile_) throws IOException
    {
        try (final FileInputStream fin = new FileInputStream(inputFile_);
             final ObjectInputStream oin = new ObjectInputStream(fin))
        {
            final ItemStatusGrid<SimpleStatus, SimpleRegressor> grid
                    = (ItemStatusGrid<SimpleStatus, SimpleRegressor>) oin.readObject();
            return grid;
        }
        catch (final ClassNotFoundException e)
        {
            throw new IOException(e);
        }

    }

    private static File generateFile(final CommandLine cmd_, final Option opt_, final boolean expectExist_) throws IOException
    {
        final String fileName = cmd_.getOptionValue(opt_.getOpt());

        if (null == fileName)
        {
            return null;
        }

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
