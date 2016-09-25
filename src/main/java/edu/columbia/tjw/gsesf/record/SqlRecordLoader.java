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
import edu.columbia.tjw.gsesf.types.LoanVendor;
import edu.columbia.tjw.item.util.HashTool;
import java.io.IOException;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
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
    private static final int BLOCK_SIZE = 10000;

    private static final String INSERT_STATEMENT = "INSERT INTO sfLoan (sfSourceId, sfLoanId, sfFileLoadId, checksum, fico, firstPaymentDate, firstTimeHomebuyer, maturityDate, msa, miPercent, numUnits, occupancy,cltv, dti, upb, "
            + " ltv, initrate, channel,  penalty, productType, propertyState, propertyType, zipCode, sourceLoanId, purpose, origTerm, numBorrowers, sfSellerId, "
            + " sfServicerId) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (sfSourceId, sfLoanId, sfLoanChecksum) DO NOTHING;";

    private static final String DATE_RECONCILE = "";

    private static final List<GseLoanField> BASE_FIELDS = Collections.unmodifiableList(Arrays.asList(new GseLoanField[]
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
    }));

    private final HashTool _hasher;
    private final DataSource _dataSource;
    private final Connection _conn;
    private final PreparedStatement _baseInsert;
    private final LoanVendor _vendor;
    private final long _vendorHash;

//    public static void doTest(final DataSource source_) throws SQLException
//    {
//        final String insert = "INSERT INTO testTable (id, id2, checkSum) VALUES (?, ?, ?) ON CONFLICT DO NOTHING";
//
//        final Connection conn = source_.getConnection();
//        final PreparedStatement stat = conn.prepareStatement(insert);
//
//        stat.setInt(1, 1);
//        stat.setInt(2, 2);
//        stat.setInt(3, 3);
//        stat.execute();
//
//        System.out.println("Executed first.");
//
//        stat.setInt(1, 1);
//        stat.setInt(2, 2);
//        stat.setInt(3, 4);
//        stat.execute();
//
//        System.out.println("Executed both.");
//
//        conn.commit();
//
//    }
//    private void reconcileDate(final String tableName_)
//    {
//
//        final String sql = "UPDATE " + tableName_ + " SET endtime = ("
//                + "SELECT MIN(starttime) "
//                + "FROM testTable t2 "
//                + "WHERE t2.id = testtable.id AND t2.id2 = testtable.id2 AND t2.starttime > testtable.starttime"
//                + ") WHERE endtime IS NULL";
//
//    }
    public SqlRecordLoader(final DataSource source_, final LoanVendor vendor_) throws SQLException
    {
        _dataSource = source_;
        _vendor = vendor_;
        _conn = _dataSource.getConnection();
        _baseInsert = _conn.prepareStatement(INSERT_STATEMENT);
        _hasher = new HashTool();
        _vendorHash = _hasher.stringToLong(_vendor.getName());
    }

    public void loadDataRecord(final DataRecord<GseLoanField> record_, final long fileLoadId_) throws SQLException
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

//        for (int w = 0; w < BASE_FIELDS.size(); w++)
//        {
//
//            //final String val = values[i];
//            final GseLoanField next = BASE_FIELDS.get(w);
//
//            switch (next)
//            {
//                case CREDIT_SCORE:
//                case FIRST_PAYMENT_DATE:
//                case FIRST_TIME_BUYER:
//                case MATURITY_DATE:
//                case MSA:
//                case MI_PERCENT:
//                case UNIT_COUNT:
//                case OCCUPANCY_STATUS:
//                case ORIG_CLTV:
//                case ORIG_DTI:
//                case ORIG_UPB:
//                case ORIG_LTV:
//                case ORIG_INTRATE:
//                case CHANNEL:
//                case PREPAYMENT_PENALTY:
//                case PRODUCT_TYPE:
//                case PROPERTY_STATE:
//                case PROPERTY_TYPE:
//                case POSTAL_CODE:
//                case LOAN_SEQUENCE_NUMBER:
//                case LOAN_PURPOSE:
//                case ORIG_TERM:
//                case NUM_BORROWERS:
//                    setParam(stat, trimmed, next, index);
//                    break;
//                case SELLER_NAME:
//                    final int sellerInt = _seller.getMemberByName(trimmed, conn_).getId();
//                    stat.setInt(index, sellerInt);
//                    break;
//                case SERVICER_NAME:
//                    final int servicerInt = _servicer.getMemberByName(trimmed, conn_).getId();
//                    stat.setInt(index, servicerInt);
//                    break;
//                default:
//                    throw new IOException("Processing error.");
//            }
//
//        }
//
//        _stat.addBatch();
    }

}
