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
import edu.columbia.tjw.gsesf.types.GseType;
import edu.columbia.tjw.gsesf.types.SqlTableBackedEnumFamily;
import edu.columbia.tjw.item.util.ByteTool;
import edu.columbia.tjw.item.util.LogUtil;
import edu.columbia.tjw.item.util.random.RandomTool;
import edu.columbia.tjw.item.util.thread.GeneralTask;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
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

    private static final int BLOCK_SIZE = 10000;

    private static final String BASE_INSERT = "INSERT INTO sfLoan (sfSourceId, sfFileLoadId, fico, firstPaymentDate, firstTimeHomebuyer, maturityDate, msa, miPercent, numUnits, occupancy,cltv, dti, upb, "
            + " ltv, initrate, channel,  penalty, productType, propertyState, propertyType, zipCode, sourceLoanId, purpose, origTerm, numBorrowers, sfSellerId, "
            + " sfServicerId) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String TIME_INSERT = "INSERT INTO sfLoanMonthStaging (stagingId, sfSourceId, sourceLoanId, reportingdate, balance, status, age, isprepaid, isdefaulted, ismodified) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String STAGING_TRANSFER = "INSERT INTO sfLoanMonth (sfSourceId, sfLoanId, sfFileLoadId, reportingdate, balance, status, age, isprepaid, isdefaulted, ismodified) \n"
            + "SELECT o.sfSourceId, o.sfLoanId, ?, s.reportingdate, s.balance, s.status, s.age, s.isprepaid, s.isdefaulted, s.ismodified\n"
            + "FROM sfLoanMonthStaging s \n"
            + "INNER JOIN sfLoan o ON s.sfSourceId = s.sfSourceId AND o.sourceLoanId = s.sourceLoanId\n"
            + "WHERE s.stagingId = ?";

    private static final String STAGING_CLEAR = "DELETE FROM sfLoanMonthStaging WHERE stagingId = ?";

    private static final String FILE_MARK = "INSERT INTO sfFileLoad (fileName, fileEntry) VALUES(?, ?)";

    private static final String FILE_COMPLETE = "UPDATE sfFileLoad SET loadComplete = clock_timestamp() WHERE sfFileLoadId = ?";

    private static final String FILE_CHECK = "SELECT sffileloadid FROM sfFileLoad o WHERE o.fileName = ? AND o.fileEntry = ?";

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

    private final File _dataDir;
    private final DataSource _dataSource;
    private final SqlTableBackedEnumFamily _servicer;
    private final SqlTableBackedEnumFamily _seller;
    //private final SqlTableBackedEnumFamily _loanIds;
    //private final DateTimeFormatter _format;

    private final List<TimeLoader> _loaders;

    public FreddieLoad(final File dataDir_, final DataSource source_) throws SQLException
    {
        _dataDir = dataDir_;
        _dataSource = source_;

        try (final Connection conn = source_.getConnection())
        {
            _servicer = new SqlTableBackedEnumFamily(conn, "sfServicer", "sfServicerId", "servicerName");
            _seller = new SqlTableBackedEnumFamily(conn, "sfSeller", "sfSellerId", "sellerName");
        }

        //_format = DateTimeFormatter.ofPattern("yyyyMM");
        _loaders = new ArrayList<>();
    }

    public void doLoad() throws SQLException, IOException
    {
        final File[] fileList = _dataDir.listFiles();

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

            loadFile(next);
        }

        for (final TimeLoader loader : _loaders)
        {
            try
            {
                loader.waitForCompletion();
            }
            catch (final Exception e)
            {
                LOG.log(Level.WARNING, "Exception loading file.", e);
            }
        }

        LOG.info("Done.");
    }

    public void loadFile(final File input_) throws SQLException, IOException
    {
        final TimeLoader tl = new TimeLoader(input_, _dataSource);
        _loaders.add(tl);

        //GeneralThreadPool.singleton().execute(tl);
        //LOG.info("Finished file.");
    }

    private static void markComplete(final Connection conn_, final long loadId_) throws SQLException
    {
        try (final PreparedStatement stat = conn_.prepareStatement(FILE_COMPLETE))
        {
            stat.setLong(1, loadId_);
            stat.executeUpdate();
        }

        conn_.commit();
    }

    private static void markFile(final Connection conn_, final ZipFile zf_, final ZipEntry entry_) throws SQLException
    {
        try (final PreparedStatement stat = conn_.prepareStatement(FILE_MARK))
        {
            final String fn = zf_.getName();
            final String entryName = entry_.getName();

            stat.setString(1, fn);
            stat.setString(2, entryName);

            stat.executeUpdate();
        }

        conn_.commit();
    }

    private static boolean isLoaded(final Connection conn_, final ZipFile zf_, final ZipEntry entry_) throws SQLException
    {
        final long loadId = getLoadId(conn_, zf_, entry_, false);
        return loadId >= 0;
    }

    private static long getLoadId(final Connection conn_, final ZipFile zf_, final ZipEntry entry_, final boolean genId_) throws SQLException
    {
        try (final PreparedStatement stat = conn_.prepareStatement(FILE_CHECK))
        {
            final String fn = zf_.getName();
            final String entryName = entry_.getName();

            stat.setString(1, fn);
            stat.setString(2, entryName);

            try (final ResultSet res = stat.executeQuery())
            {
                if (res.next())
                {
                    return res.getLong(1);
                }
            }
        }

        if (!genId_)
        {
            return -1;
        }

        markFile(conn_, zf_, entry_);
        return getLoadId(conn_, zf_, entry_, false);
    }

    private final class TimeLoader extends GeneralTask<TimeLoader>
    {
        private final ZipFile _zf;
        private final ZipEntry _entry;
        private final ZipEntry _baseEntry;
        private final DataSource _source;
        boolean _complete = false;

        public TimeLoader(final File input_, final DataSource dataSource_) throws IOException
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

            _zf = zf;
            _entry = timeEntry;
            _baseEntry = baseEntry;
            _source = dataSource_;
        }

        @Override
        protected TimeLoader subRun()
        {
            try (final Connection conn = _source.getConnection())
            {
                if (!isLoaded(conn, _zf, _baseEntry))
                {
                    LOG.info("Loading entry: " + _baseEntry.getName());
                    final long loadId = getLoadId(conn, _zf, _baseEntry, true);

                    try (final InputStream baseStream = _zf.getInputStream(_baseEntry))
                    {
                        loadBaseEntry(baseStream, conn, loadId);
                        markComplete(conn, loadId);
                        LOG.info("Done loading entry: " + _baseEntry.getName());
                    }
                    catch (final Exception e)
                    {
                        LOG.log(Level.WARNING, "Exception caught, entry not loaded: " + _baseEntry.getName(), e);
                    }
                }
                else
                {
                    LOG.info("Entry already loaded: " + _baseEntry.getName());
                }

                final String entryName = _entry.getName();

                if (!isLoaded(conn, _zf, _entry))
                {
                    LOG.info("Loading entry: " + entryName);
                    final long timeEntryId = getLoadId(conn, _zf, _entry, true);

                    try (final InputStream timeStream = _zf.getInputStream(_entry))
                    {
                        loadTimeEntry(timeStream, conn, timeEntryId);

                        LOG.info("Committing time entry: " + entryName);
                        markComplete(conn, timeEntryId);
                        LOG.info("Time entry complete: " + entryName);
                    }
                    catch (final Exception e)
                    {
                        LOG.log(Level.WARNING, "Exception caught, entry not loaded: " + entryName, e);
                    }
                }
                else
                {
                    LOG.info("Entry already loaded: " + entryName);
                }

            }
            catch (final Exception e)
            {
                LOG.log(Level.WARNING, "Exception caught.", e);
                throw new RuntimeException(e);
            }

            return this;
        }
    }

    private void loadBaseEntry(final InputStream stream_, final Connection conn_, final long loadId_) throws IOException, SQLException
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
                    stat.setLong(2, loadId_);
                    final int offset = 3;

                    for (int i = 0; i < BASE_FIELDS.length; i++)
                    {
                        final String val = values[i];
                        final GseLoanField next = BASE_FIELDS[i];

                        String trimmed = val.trim();

                        if (trimmed.isEmpty())
                        {
                            trimmed = null;
                        }

                        final int index = i + offset;

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
                                setParam(stat, trimmed, next, index);
                                break;
                            case SELLER_NAME:
                                final int sellerInt = _seller.getMemberByName(trimmed, conn_).getId();
                                stat.setInt(index, sellerInt);
                                break;
                            case SERVICER_NAME:
                                final int servicerInt = _servicer.getMemberByName(trimmed, conn_).getId();
                                stat.setInt(index, servicerInt);
                                break;
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

        conn_.commit();
    }

    private static void setParam(final PreparedStatement stat_, final String input_, final GseLoanField field_, final int paramNumber_) throws SQLException
    {
        final GseType type = field_.getType();

        switch (type)
        {
            case DOUBLE:
            {
                if (null == input_)
                {
                    stat_.setNull(paramNumber_, java.sql.Types.DOUBLE);
                }
                else
                {
                    final double val = Double.parseDouble(input_);
                    stat_.setDouble(paramNumber_, val);
                }

                break;
            }
            case INT:
            {
                if (null == input_)
                {
                    stat_.setNull(paramNumber_, java.sql.Types.INTEGER);
                }
                else
                {
                    final int val = Integer.parseInt(input_);
                    stat_.setInt(paramNumber_, val);
                }

                break;
            }
            case STRING:
            {
                stat_.setString(paramNumber_, input_);
                break;
            }
            case BOOLEAN:
            {
                if (null == input_)
                {
                    stat_.setNull(paramNumber_, java.sql.Types.BOOLEAN);
                }
                else
                {
                    final boolean val = "Y".equals(input_);
                    stat_.setBoolean(paramNumber_, val);
                }

                break;
            }
            case DATE:
            {
                if (null == input_)
                {
                    stat_.setNull(paramNumber_, java.sql.Types.DATE);
                }
                else
                {
                    final String expanded = input_ + "01";
                    final LocalDate date = LocalDate.from(DateTimeFormatter.BASIC_ISO_DATE.parse(expanded));
                    final Instant inst = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
                    final long ms = inst.toEpochMilli();
                    final java.sql.Date sqlDate = new java.sql.Date(ms);
                    stat_.setDate(paramNumber_, sqlDate);
                }

                break;
            }
            default:
                throw new IllegalArgumentException("Unknown type: " + type);
        }

    }

    private static void loadTimeEntry(final InputStream stream_, final Connection conn_, final long fileLoadId_) throws IOException, SQLException
    {
        LOG.info("Loading time file.");

        final String stagingId = ByteTool.bytesToHex(RandomTool.getStrong(16));

        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(stream_)))
        {
            try (final PreparedStatement stat = conn_.prepareStatement(TIME_INSERT))
            {
                String line = reader.readLine();

                int lineCount = 0;

                while (null != line)
                {
                    lineCount++;
                    final String[] values = line.split("\\|");

                    //We know a-priori that the freddie source ID is 1. 
                    stat.setString(1, stagingId);
                    stat.setInt(2, 1);

                    for (int i = 0; i < TIME_FIELDS.length; i++)
                    {
                        if (i >= values.length)
                        {
                            continue;
                        }

                        final String val = values[i];
                        final GseLoanField next = TIME_FIELDS[i];

                        if (null == next)
                        {
                            //This is a field we don't care about, skip it. 
                            continue;
                        }

                        String trimmed = val.trim();

                        if (trimmed.isEmpty())
                        {
                            trimmed = null;
                        }

                        switch (next)
                        {
                            case LOAN_SEQUENCE_NUMBER:
                            case FACTOR_DATE:
                            case STATUS:
                            case AGE:
                                setParam(stat, trimmed, next, i + 3);
                                break;
                            case UPB:
                                if (null == trimmed)
                                {
                                    stat.setDouble(i + 3, 0.0);
                                }
                                else
                                {
                                    setParam(stat, trimmed, next, i + 3);
                                }
                                break;
                            case IS_MODIFIED:
                                if (null == trimmed)
                                {
                                    stat.setBoolean(10, false);
                                }
                                else
                                {
                                    setParam(stat, trimmed, next, 10);
                                }

                                break;
                            case ZERO_BAL_CODE:
                            {
                                final boolean prepaid = "01".equals(trimmed);
                                final boolean defaulted = "03".equals(trimmed) || "09".equals(trimmed) || "09".equals(trimmed);

                                stat.setBoolean(8, prepaid);
                                stat.setBoolean(9, defaulted);

                                break;
                            }
                            default:
                                throw new IOException("Processing error.");
                        }

                    }

                    stat.addBatch();

                    if (lineCount % BLOCK_SIZE == 0)
                    {
                        LOG.info("Executing batch: " + lineCount);
                        stat.executeBatch();
                        conn_.commit();
                    }

                    line = reader.readLine();
                }

                stat.executeBatch();
                conn_.commit();
            }

        }

        conn_.commit();

        try (final PreparedStatement stat = conn_.prepareStatement(STAGING_TRANSFER))
        {
            LOG.info("Transferring results out of staging table.");
            stat.setLong(1, fileLoadId_);
            stat.setString(2, stagingId);
            stat.executeUpdate();
        }

        conn_.commit();

        try (final PreparedStatement stat = conn_.prepareStatement(STAGING_CLEAR))
        {
            LOG.info("Clearing staging table.");
            stat.setString(1, stagingId);
            stat.executeUpdate();
        }

        conn_.commit();
        LOG.info("Completed loading time data.");
    }

}
