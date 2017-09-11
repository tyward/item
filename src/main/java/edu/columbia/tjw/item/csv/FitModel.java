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

import edu.columbia.tjw.item.ItemParameters;
import edu.columbia.tjw.item.base.SimpleRegressor;
import edu.columbia.tjw.item.base.SimpleStatus;
import edu.columbia.tjw.item.base.StandardCurveFactory;
import edu.columbia.tjw.item.base.StandardCurveType;
import edu.columbia.tjw.item.data.ItemStatusGrid;
import edu.columbia.tjw.item.fit.ItemFitter;
import edu.columbia.tjw.item.optimize.ConvergenceException;
import edu.columbia.tjw.item.visualize.ModelVisualizer;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
public final class FitModel
{
    private static final Option DESCRIPTOR_FILE = new Option("descriptor_file", true,
            "The file containing the data descriptor previously compiled");
    private static final Option COMPILED_DATA = new Option("compiled_data", true,
            "The CSV data to be processed");
    private static final Option STARTING_MODEL = new Option("starting_model", true,
            "(Optional) The model to use as a starting point.");
    private static final Option OUTPUT_FILE = new Option("model_output", true,
            "The file where the produced model is written.");
    private static final Option CURVE_SET = new Option("curve_columns", true,
            "(Optional) The set of columns on which curves are allowed, [default all].");
    private static final Option CURVE_COUNT = new Option("curve_count", true,
            "(Optional) How many curves to add, [default 0].");
    private static final Option RUN_ANNEALING = new Option("run_annealing", true,
            "(Optional) Run annealing or not [default false].");

    private FitModel()
    {
    }

    public static void main(final String[] args_)
    {
        DESCRIPTOR_FILE.setRequired(true);
        COMPILED_DATA.setRequired(true);
        STARTING_MODEL.setRequired(false);
        OUTPUT_FILE.setRequired(true);
        CURVE_SET.setRequired(false);
        CURVE_COUNT.setRequired(false);
        RUN_ANNEALING.setRequired(false);

        final Options opt = new Options();
        opt.addOption(DESCRIPTOR_FILE);
        opt.addOption(COMPILED_DATA);
        opt.addOption(STARTING_MODEL);
        opt.addOption(OUTPUT_FILE);
        opt.addOption(CURVE_SET);
        opt.addOption(CURVE_COUNT);
        opt.addOption(RUN_ANNEALING);

        try
        {
            final CommandLineParser parser = new DefaultParser();
            final CommandLine cmd = parser.parse(opt, args_);

            final File descriptor = generateFile(cmd, DESCRIPTOR_FILE, true);
            final File input_file = generateFile(cmd, COMPILED_DATA, true);
            final File starting_file = generateFile(cmd, STARTING_MODEL, true);
            final File output_file = generateFile(cmd, OUTPUT_FILE, false);

            final String curveSetString = cmd.getOptionValue(CURVE_SET.getOpt(), null);
            final String curveCountString = cmd.getOptionValue(CURVE_COUNT.getOpt(), "0");
            final String runAnnealingString = cmd.getOptionValue(RUN_ANNEALING.getOpt(), "false");

            final int curveCount = Integer.parseInt(curveCountString);
            final boolean runAnnealing = Boolean.parseBoolean(runAnnealingString);

            final CompiledDataDescriptor compiled = getCompiled(descriptor);
            final ItemStatusGrid<SimpleStatus, SimpleRegressor> grid = loadGrid(input_file);
            final ItemParameters<SimpleStatus, SimpleRegressor, StandardCurveType> params = loadParams(starting_file);

            final SimpleRegressor intercept = grid.getRegressorFamily().getFromName("INTERCEPT");
            final SimpleStatus stat = grid.getStatusFamily().getFromOrdinal(grid.getStatus(0));

            final ItemFitter<SimpleStatus, SimpleRegressor, StandardCurveType> fitter
                    = new ItemFitter<>(new StandardCurveFactory(), intercept, stat, grid);

            final SortedSet<SimpleRegressor> curveRegs;

            if (null != curveSetString)
            {
                curveRegs = new TreeSet<>();
                final String[] split = curveSetString.split(",");

                for (final String next : split)
                {
                    final SimpleRegressor reg = compiled.getRegressorFamily().getFromName(next);

                    if (null == reg)
                    {
                        throw new IllegalArgumentException("Unknown regressor: " + next);
                    }

                    if (!compiled.getCurveRegs().contains(reg))
                    {
                        throw new IllegalArgumentException("Not curve regressor (perhaps defined as boolean flag?): " + next);
                    }

                    curveRegs.add(reg);
                }
            }
            else
            {
                curveRegs = compiled.getCurveRegs();
            }

            if (null != params)
            {
                fitter.pushParameters("Starting Params", params);
            }

            fitter.fitCoefficients();
            fitter.addCoefficients(compiled.getFlagRegs());
            fitter.fitCoefficients();
            fitter.calibrateCurves();
            fitter.trim(false);

            System.out.println("Initial fitting complete: " + fitter.getChain().toString());

            if (curveCount > 0)
            {
                System.out.println("Expanding model, available regressors: " + curveCount);
                fitter.expandModel(curveRegs, curveCount);
                fitter.calibrateCurves();
                fitter.trim(true);
                System.out.println("Model Expansion complete: " + fitter.getChain().toString());
            }

            if (runAnnealing)
            {
                System.out.println("Running annealing");
                fitter.runAnnealingByEntry(curveRegs, true);
                System.out.println("Annealing complete: " + fitter.getChain().toString());
            }

            System.out.println("\n\n\n\nVisualizing model: ");
            final ModelVisualizer<SimpleStatus, SimpleRegressor, StandardCurveType> vis = new ModelVisualizer<>(fitter.getBestParameters(), grid, compiled.getRegressorFamily().getMembers());
            vis.printResults(System.out);

            System.out.println("\n\n\n\nFinal Chain: " + fitter.getChain().toString());

            writeParams(output_file, fitter.getBestParameters());

            System.out.println("Model written to file: " + output_file.getAbsolutePath());

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
        catch (final ConvergenceException e)
        {
            e.printStackTrace();
        }
    }

    private static void writeParams(final File outputFile_,
            final ItemParameters<SimpleStatus, SimpleRegressor, StandardCurveType> params_) throws IOException
    {
        try (final FileOutputStream fOut = new FileOutputStream(outputFile_);
                final ObjectOutputStream oOut = new ObjectOutputStream(fOut))
        {
            oOut.writeObject(params_);
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
