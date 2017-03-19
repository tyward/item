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
import edu.columbia.tjw.gsesf.types.HashTableBackedEnumFamily;
import edu.columbia.tjw.gsesf.types.LoanVendor;
import edu.columbia.tjw.gsesf.types.RawDataType;
import edu.columbia.tjw.item.util.HashTool;
import edu.columbia.tjw.item.util.ListTool;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import javax.sql.DataSource;

/**
 * This class is designed to load record files into our SQL database, and
 * similarly extract records from the DB.
 *
 * @author tyler
 */
public class SqlRecordLoader
{
    private static final int BLOCK_SIZE = 10 * 1000;

    private static final String INSERT_STATEMENT = "INSERT INTO sfLoan (sfSourceId, sfLoanId, sfFileLoadId, sfLoanChecksum, fico, firstPaymentDate, firstTimeHomebuyer, maturityDate, msa, miPercent, numUnits, occupancy,cltv, dti, upb, "
            + " ltv, initrate, channel,  penalty, productType, propertyState, propertyType, zipCode, sourceLoanId, purpose, origTerm, numBorrowers, sfSellerId, "
            + " sfServicerId) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?) ON CONFLICT (sfSourceId, sfLoanId, sfLoanChecksum) DO NOTHING;";

    private static final String TIME_INSERT = "INSERT INTO sfLoanMonth ( "
            + " sfSourceId,"
            + " sfLoanId,"
            + " sfLoanChecksum,"
            + " sfLoanMonthChecksum,"
            + " sfRecordStart,"
            + " sfRecordEnd,"
            + " sfFileLoadId,"
            + " reportingDate,"
            + " balance,"
            + " status,"
            + " age,"
            + " isPrepaid,"
            + " isDefaulted,"
            + " isModified) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?) ON CONFLICT (sfSourceId, sfLoanId, sfLoanChecksum) DO NOTHING;";

    private static final String BASE_RECONCILE = "UPDATE sfLoan SET sfRecordEnd = ("
            + "SELECT MIN(sfRecordStart) FROM sfLoan t2 WHERE t2.sfSourceId = sfLoan.sfSourceId AND t2.sfLoanId = sfLoan.sfLoanId AND t2.sfRecordStart > sfLoan.sfRecordStart"
            + ") WHERE sfRecordEnd IS NULL";

    private static final String FILE_CHECK = "SELECT sffileloadid FROM sfFileLoad o WHERE o.fileName = ? AND o.fileEntry = ?";

    private static final String FILE_MARK = "INSERT INTO sfFileLoad (fileName, fileEntry) VALUES(?, ?)";

    private static final String FILE_COMPLETE = "UPDATE sfFileLoad SET loadComplete = clock_timestamp() WHERE sfFileLoadId = ?";

    private static final List<GseLoanField> BASE_FIELDS = ListTool.listify(GseLoanField.class,
            GseLoanField.CREDIT_SCORE,
            GseLoanField.FIRST_PAYMENT_DATE,
            GseLoanField.FIRST_TIME_BUYER,
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
            GseLoanField.SERVICER_NAME);

    private static final List<GseLoanField> TIME_FIELDS = ListTool.listify(GseLoanField.class,
            GseLoanField.LOAN_SEQUENCE_NUMBER,
            GseLoanField.FACTOR_DATE,
            GseLoanField.UPB,
            GseLoanField.STATUS,
            GseLoanField.AGE,
            GseLoanField.IS_MODIFIED,
            GseLoanField.ZERO_BAL_CODE);

    private final HashTool _hasher;
    private final DataSource _dataSource;
    private final Connection _conn;
    private final PreparedStatement _baseInsert;
    private final LoanVendor _vendor;
    private final long _vendorHash;
    private final HashTableBackedEnumFamily _servicer;
    private final HashTableBackedEnumFamily _seller;

    public SqlRecordLoader(final DataSource source_, final LoanVendor vendor_) throws SQLException
    {
        _dataSource = source_;
        _vendor = vendor_;
        _conn = _dataSource.getConnection();
        _baseInsert = _conn.prepareStatement(INSERT_STATEMENT);
        _hasher = new HashTool();
        _vendorHash = _hasher.stringToLong(_vendor.getName());
        _servicer = new HashTableBackedEnumFamily(_conn, "sfServicer", "sfServicerId", "servicerName");
        _seller = new HashTableBackedEnumFamily(_conn, "sfSeller", "sfSellerId", "sellerName");
    }

    private void markComplete(final long loadId_) throws SQLException
    {
        //Make sure to flush all outstanding data...
        _conn.commit();

        try (final PreparedStatement stat = _conn.prepareStatement(FILE_COMPLETE))
        {
            stat.setLong(1, loadId_);
            stat.executeUpdate();
        }

        _conn.commit();
    }

    private void markFile(final String file_name_, final String type_) throws SQLException
    {
        try (final PreparedStatement stat = _conn.prepareStatement(FILE_MARK))
        {
            stat.setString(1, file_name_);
            stat.setString(2, type_);

            stat.executeUpdate();
        }

        _conn.commit();
    }

    private long getLoadId(final String file_name_, final String type_, final boolean genId_) throws SQLException
    {
        try (final PreparedStatement stat = _conn.prepareStatement(FILE_CHECK))
        {
            stat.setString(1, file_name_);
            stat.setString(2, type_);

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

        markFile(file_name_, type_);
        return getLoadId(file_name_, type_, false);
    }

    private void reconcileDatesBase() throws SQLException
    {
//        flushBase();
//
//        try (final Statement stat = _conn.createStatement())
//        {
//            stat.executeUpdate(BASE_RECONCILE);
//            stat.close();
//        }
//
//        _conn.commit();
    }

    private void flushBase() throws SQLException
    {
        _baseInsert.executeBatch();
        _conn.commit();

    }

    public void loadTimeFile(final File inputFile_, final boolean isZip_) throws IOException, SQLException
    {
        final RawRecordReader<GseLoanField> reader = new RawRecordReader<>(inputFile_, GseLoanField.FAMILY, isZip_);

        long count = 0;
        long sub_count = 0;

        final long loadId = getLoadId(inputFile_.getName(), "time", true);

        for (final DataRecord<GseLoanField> next : reader)
        {
            loadDataRecord(next, loadId);
            sub_count++;
            count++;

            if (count % BLOCK_SIZE == 0)
            {
                flushBase();
                sub_count = 0;
            }

        }

        if (sub_count > 0)
        {
            flushBase();
        }

        reconcileDatesBase();
        markComplete(loadId);
    }

    public void loadBaseFile(final File inputFile_, final boolean isZip_) throws IOException, SQLException
    {
        final RawRecordReader<GseLoanField> reader = new RawRecordReader<>(inputFile_, GseLoanField.FAMILY, isZip_);

        long count = 0;
        long sub_count = 0;

        final long loadId = getLoadId(inputFile_.getName(), "base", true);

        for (final DataRecord<GseLoanField> next : reader)
        {
            loadDataRecord(next, loadId);
            sub_count++;
            count++;

            if (count % BLOCK_SIZE == 0)
            {
                flushBase();
                sub_count = 0;
            }
        }

        if (sub_count > 0)
        {
            flushBase();
        }

        reconcileDatesBase();
        markComplete(loadId);
    }

    private void loadTimeRecord(final DataRecord<GseLoanField> record_, final long fileLoadId_) throws SQLException
    {

    }

    private void loadDataRecord(final DataRecord<GseLoanField> record_, final long fileLoadId_) throws SQLException
    {
        _baseInsert.setInt(1, _vendor.getId());

        final String sourceName = record_.getString(GseLoanField.LOAN_SEQUENCE_NUMBER);
        final long nameHash = _hasher.stringToLong(sourceName);
        final long combined = nameHash + _vendorHash;

        _baseInsert.setLong(2, combined);
        _baseInsert.setLong(3, fileLoadId_);

        //Skip index 4, for now. That's a checksum, we'll come back to it.
        _hasher.updateLong(combined);

        final int offset = 5; //where do the data fields start...

        for (int w = 0; w < BASE_FIELDS.size(); w++)
        {
            final GseLoanField next = BASE_FIELDS.get(w);
            final int index = offset + w;

            switch (next)
            {
                case CREDIT_SCORE:
                case MSA:
                case UNIT_COUNT:
                case POSTAL_CODE:
                case ORIG_TERM:
                case NUM_BORROWERS:
                case FIRST_PAYMENT_DATE:
                case MATURITY_DATE:
                case FIRST_TIME_BUYER:
                case PREPAYMENT_PENALTY:
                case MI_PERCENT:
                case ORIG_CLTV:
                case ORIG_DTI:
                case ORIG_UPB:
                case ORIG_LTV:
                case ORIG_INTRATE:
                case OCCUPANCY_STATUS:
                case CHANNEL:
                case PRODUCT_TYPE:
                case PROPERTY_STATE:
                case PROPERTY_TYPE:
                case LOAN_SEQUENCE_NUMBER:
                case LOAN_PURPOSE:
                {
                    incorporateData(next, record_, index);
                    break;
                }
                case SELLER_NAME:
                {
                    final String seller_name = record_.getString(next);
                    final long seller_hash = _seller.generateMemberId(seller_name);
                    _hasher.updateLong(seller_hash);
                    _baseInsert.setLong(index, seller_hash);
                    break;
                }
                case SERVICER_NAME:
                {
                    final String name = record_.getString(next);
                    final long hash = _servicer.generateMemberId(name);
                    _hasher.updateLong(hash);
                    _baseInsert.setLong(index, hash);
                    break;
                }
                default:
                    throw new SQLException("Processing error.");
            }
        }

        //Now fill in the checksum.
        final long checksum = _hasher.doHashLong();
        _baseInsert.setLong(4, checksum);

        _baseInsert.addBatch();
    }

    private void incorporateData(final GseLoanField next_, final DataRecord<GseLoanField> record_, final int index_) throws SQLException
    {
        final RawDataType type = next_.getType();

        switch (type)
        {
            case DOUBLE:
            {
                final double val = record_.getDouble(next_);
                _hasher.updateDouble(val);
                _baseInsert.setDouble(index_, val);
                break;
            }
            case STRING:
            {
                final String val = record_.getString(next_);
                _hasher.updateString(val);
                _baseInsert.setString(index_, val);
                break;
            }
            case BOOLEAN:
            {
                final boolean val = record_.getBoolean(next_);
                _hasher.updateBoolean(val);
                _baseInsert.setBoolean(index_, val);
                break;
            }
            case INT:
            {
                final int val = record_.getInt(next_);
                _hasher.updateLong(val);
                _baseInsert.setInt(index_, val);
                break;
            }
            case DATE:
            {
                final LocalDate date = record_.getDate(next_);
                final Instant inst = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
                final long ms = inst.toEpochMilli();

                _hasher.updateLong(ms);

                final java.sql.Date sqlDate = new java.sql.Date(ms);
                _baseInsert.setDate(index_, sqlDate);
                break;
            }
            default:
                throw new SQLException("Unknown type name: " + type);
        }
    }

}