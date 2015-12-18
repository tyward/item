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
package edu.columbia.tjw.gsesf;

import edu.columbia.tjw.gsesf.types.GseLoanField;
import edu.columbia.tjw.item.util.LogUtil;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.sql.DataSource;

/**
 *
 * @author tyler
 */
public final class FreddieLoad
{
    private static final Logger LOG = LogUtil.getLogger(FreddieLoad.class);

    private final int BLOCK_SIZE = 10000;

    private static final String BASE_INSERT = "INSERT INTO sfLoan (sfSourceId, fico, firstPaymentDate, firstTimeHomebuyer, maturityDate, msa, miPercent, numUnits, occupancy,cltv, dti, upb, "
            + " ltv, initrate, channel,  penalty, productType, propertyState, propertyType, zipCode, sourceLoanId, purpose, origTerm, numBorrowers, sfSellerId, "
            + " sfServicerId) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

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

    private final File _dataDir;
    private final DataSource _dataSource;

    public FreddieLoad(final File dataDir_, final DataSource source_)
    {
        _dataDir = dataDir_;
        _dataSource = source_;
    }

    public void doLoad() throws SQLException, IOException
    {
        final File[] fileList = _dataDir.listFiles();

        try (final Connection conn = _dataSource.getConnection())
        {
            LOG.info("Examining directory: " + _dataDir.getAbsolutePath());
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

                loadFile(next, conn);
            }
        }
    }

    public void loadFile(final File input_, final Connection conn_) throws SQLException, IOException
    {
        final ZipFile zf = new ZipFile(input_);

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
            else
            {
                if (null == baseEntry)
                {
                    baseEntry = entry;
                }
                else
                {
                    throw new IOException("Two base files in one zip file: " + entryName);
                }
            }
        }

        if (null == baseEntry || null == timeEntry)
        {
            throw new IOException("Missing one of the expected entries.");
        }

        try (final InputStream baseStream = zf.getInputStream(baseEntry))
        {
            loadBaseEntry(baseStream, conn_);
        }

        try (final InputStream timeStream = zf.getInputStream(timeEntry))
        {
            loadTimeEntry(timeStream, conn_);
        }

        LOG.info("Finished file.");
    }

    private void loadBaseEntry(final InputStream stream_, final Connection conn_) throws IOException, SQLException
    {
        LOG.info("Loading base file.");

        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(stream_)))
        {
            try (final PreparedStatement stat = conn_.prepareStatement(BASE_INSERT))
            {
                String line = reader.readLine();

                int lineCount = 0;

                while (null != line)
                {
                    lineCount++;
                    final String[] values = line.split("\\|");

                    if (values.length != BASE_FIELDS.length)
                    {
                        throw new IOException("Malformed line.");
                    }

                    //We know a-priori that the freddie source ID is 1. 
                    stat.setInt(1, 1);

                    for (int i = 0; i < BASE_FIELDS.length; i++)
                    {
                        final String val = values[i];
                        final GseLoanField next = BASE_FIELDS[i];

                        String trimmed = val.trim();

                        if (trimmed.isEmpty())
                        {
                            trimmed = null;
                        }

                        switch (next)
                        {
                            case CREDIT_SCORE:
                            case FIRST_PAYMENT_DATE:
                            case FIRST_TIME_BUYER:
                            case MATURITY_DATE:
                            case MSA:
                            case MI_PERCENT:
                            case UNIT_COUNT:
                            case OCCUPANCY_STATUS:
                            case ORIG_CLTV:
                            case ORIG_DTI:
                            case ORIG_UPB:
                            case ORIG_LTV:
                            case ORIG_INTRATE:
                            case CHANNEL:
                            case PREPAYMENT_PENALTY:
                            case PRODUCT_TYPE:
                            case PROPERTY_STATE:
                            case PROPERTY_TYPE:
                            case POSTAL_CODE:
                            case LOAN_SEQUENCE_NUMBER:
                            case LOAN_PURPOSE:
                            case ORIG_TERM:
                            case NUM_BORROWERS:
                                setParam(stat, trimmed, next, i + 2);
                                break;
                            case SELLER_NAME:
                            case SERVICER_NAME:
                            default:
                                throw new IOException("Processing error.");
                        }

                    }

                    stat.addBatch();

                    if (lineCount % BLOCK_SIZE == 0)
                    {
                        LOG.info("Executing batch: " + lineCount);
                        stat.executeBatch();
                    }

                    line = reader.readLine();
                }

                stat.executeBatch();

            }

        }

    }

    private void setParam(final PreparedStatement stat_, final String input_, final GseLoanField field_, final int paramNumber_)
    {

    }

    private void loadTimeEntry(final InputStream stream_, final Connection conn_) throws IOException, SQLException
    {
        LOG.info("Loading time file.");

    }

}
