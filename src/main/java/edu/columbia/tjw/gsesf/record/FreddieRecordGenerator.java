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
package edu.columbia.tjw.gsesf.record;

import edu.columbia.tjw.gsesf.types.GseLoanField;
import edu.columbia.tjw.item.util.LogUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 *
 * @author tyler
 */
public final class FreddieRecordGenerator
{
    private static final Logger LOG = LogUtil.getLogger(FreddieRecordGenerator.class);

    private static final GseLoanField[] TIME_FIELDS = new GseLoanField[]
    {
        GseLoanField.LOAN_SEQUENCE_NUMBER,
        GseLoanField.FACTOR_DATE,
        GseLoanField.UPB,
        GseLoanField.STATUS,
        GseLoanField.AGE,
        null,
        null,
        GseLoanField.IS_MODIFIED,
        GseLoanField.ZERO_BAL_CODE,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null
    };

    private static final GseLoanField[] BASE_FIELDS = new GseLoanField[]
    {
        GseLoanField.CREDIT_SCORE,
        GseLoanField.FIRST_PAYMENT_DATE,
        GseLoanField.FIRST_TIME_BUYER, //
        GseLoanField.MATURITY_DATE,
        GseLoanField.MSA,
        GseLoanField.MI_PERCENT,
        GseLoanField.UNIT_COUNT,
        GseLoanField.OCCUPANCY_STATUS,
        GseLoanField.ORIG_CLTV,
        GseLoanField.ORIG_DTI,
        GseLoanField.ORIG_UPB,
        GseLoanField.ORIG_LTV,
        GseLoanField.ORIG_INTRATE,
        GseLoanField.CHANNEL,
        GseLoanField.PREPAYMENT_PENALTY,
        GseLoanField.PRODUCT_TYPE,
        GseLoanField.PROPERTY_STATE,
        GseLoanField.PROPERTY_TYPE,
        GseLoanField.POSTAL_CODE,
        GseLoanField.LOAN_SEQUENCE_NUMBER,
        GseLoanField.LOAN_PURPOSE,
        GseLoanField.ORIG_TERM,
        GseLoanField.NUM_BORROWERS,
        GseLoanField.SELLER_NAME,
        GseLoanField.SERVICER_NAME
    };

    private final File _inputDir;
    private final File _outputDir;

    private final List<File> _zipFiles;

    public FreddieRecordGenerator(final File inputDir_, final File outputDir_)
    {
        _inputDir = inputDir_;
        _outputDir = outputDir_;

        _outputDir.mkdirs();

        final File[] fileList = _inputDir.listFiles();
        final List<File> zipFiles = new ArrayList<>(fileList.length);

        LOG.info("Examining directory: " + _inputDir.getAbsolutePath());
        LOG.info("Files found: " + fileList.length);

        for (final File next : fileList)
        {
            LOG.info("Examining file: " + next.getAbsolutePath());

            final String fileName = next.getName();

            if (!fileName.endsWith(".zip") || fileName.startsWith("."))
            {
                LOG.info("Not a zip file, skipping.");
                continue;
            }

            zipFiles.add(next);
        }

        _zipFiles = Collections.unmodifiableList(zipFiles);
    }

    public List<File> getFileList()
    {
        return _zipFiles;
    }

    public void loadAll() throws IOException
    {
        for (final File next : _zipFiles)
        {
            final String fileName = next.getName();
            final String[] fragments = fileName.split("_");
            final String root = fragments[fragments.length - 1];
            final String[] f2 = root.split("\\.");
            final String fnBase = f2[0];

            loadFile(next, fnBase);
        }
    }

    public void loadFile(final File zipFile_, final String outName_) throws IOException
    {
        final ZipFile zf = new ZipFile(zipFile_);
        final Enumeration<? extends ZipEntry> entries = zf.entries();

        LOG.info("Got zip entries.");

        ZipEntry baseEntry = null;
        ZipEntry timeEntry = null;

        for (final ZipEntry entry : Collections.list(entries))
        {
            final String entryName = entry.getName();
            LOG.info("Found an entry: " + entryName);

            if (entryName.contains("_time_"))
            {
                if (null == timeEntry)
                {
                    timeEntry = entry;
                }
                else
                {
                    throw new IOException("Two time files in one zip file: " + entryName);
                }
            }
            else if (null == baseEntry)
            {
                baseEntry = entry;
            }
            else
            {
                throw new IOException("Two base files in one zip file: " + entryName);
            }
        }

        if (null == baseEntry || null == timeEntry)
        {
            throw new IOException("Missing one of the expected entries.");
        }

        final FreddieRecordReader<GseLoanField> baseReader = new FreddieRecordReader<>(BASE_FIELDS, GseLoanField.FAMILY, zf, baseEntry);
        final FreddieRecordReader<GseLoanField> timeReader = new FreddieRecordReader<>(TIME_FIELDS, GseLoanField.FAMILY, zf, timeEntry);

        LOG.info("Starting base output.");

        final File baseOutput = new File(_outputDir, outName_ + "_base.dat.gz");

        if (baseOutput.exists())
        {
            throw new IOException("Unwilling to overwrite existing file: " + baseOutput.getCanonicalPath());
        }

        final RecordWriter<GseLoanField> baseWriter = new RecordWriter<>(baseReader.getHeader(), new FileOutputStream(baseOutput), true);
        baseWriter.writeAllRecords(baseReader);
        baseWriter.close();

        LOG.info("Base output complete. ");

        final File timeOutput = new File(_outputDir, outName_ + "_time.dat.gz");

        if (timeOutput.exists())
        {
            throw new IOException("Unwilling to overwrite existing file: " + timeOutput.getCanonicalPath());
        }

        final RecordWriter<GseLoanField> timeWriter = new RecordWriter<>(timeReader.getHeader(), new FileOutputStream(timeOutput), true);
        timeWriter.writeAllRecords(timeReader);
        timeWriter.close();

        LOG.info("Time output complete.");
    }

}
