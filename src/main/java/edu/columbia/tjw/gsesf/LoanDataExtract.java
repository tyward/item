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
import edu.columbia.tjw.item.data.ItemStatusGrid;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.sql.DataSource;

/**
 *
 * @author tyler
 */
public final class LoanDataExtract implements AutoCloseable
{
    private static final String SQL_STRING = "SELECT s.sfsourceName, o.sfSourceId, o.sfLoanId, m.reportingdate, o.fico, o.firstPaymentDate, o.maturityDate, o.msa, o.mipercent, o.numunits, o.cltv, "
            + " o.dti, o.upb, \n"
            + " o.initrate, o.channel, o.occupancy, o.producttype, o.propertystate, o.propertytype, o.zipcode, o.purpose, o.firsttimehomebuyer, \n"
            + " m.balance, m.status, m.isprepaid, m.isdefaulted, m.ismodified\n"
            + " FROM sfLoan o\n"
            + " INNER JOIN sfLoanMonth m ON m.sfsourceId = o.sfsourceId AND m.sfLoanId = o.sfLoanId\n"
            + " INNER JOIN sfSource s ON s.sfSourceId = o.sfSourceId\n"
            + " ORDER BY z.sfsourceId, z.sfLoanId, z.reportingDate";

    private static final List<GseLoanField> FIELDS;

    static
    {
        final GseLoanField[] fields = new GseLoanField[]
        {
            GseLoanField.SOURCE_NAME,
            null,
            GseLoanField.LOAN_SEQUENCE_NUMBER,
            GseLoanField.FACTOR_DATE,
            GseLoanField.CREDIT_SCORE,
            GseLoanField.FIRST_PAYMENT_DATE,
            GseLoanField.MATURITY_DATE,
            GseLoanField.MSA,
            GseLoanField.MI_PERCENT,
            GseLoanField.UNIT_COUNT,
            GseLoanField.ORIG_CLTV,
            GseLoanField.ORIG_DTI,
            GseLoanField.ORIG_UPB,
            GseLoanField.INTRATE,
            GseLoanField.CHANNEL,
            GseLoanField.OCCUPANCY_STATUS,
            GseLoanField.PRODUCT_TYPE,
            GseLoanField.PROPERTY_STATE,
            GseLoanField.PROPERTY_TYPE,
            GseLoanField.POSTAL_CODE,
            GseLoanField.LOAN_PURPOSE,
            GseLoanField.FIRST_TIME_BUYER,
            GseLoanField.UPB,
            GseLoanField.STATUS,
            GseLoanField.IS_PREPAID,
            GseLoanField.IS_DEFAULTED,
            GseLoanField.IS_MODIFIED
        };

        final List<GseLoanField> rawList = Arrays.asList(fields);
        FIELDS = Collections.unmodifiableList(rawList);
    }

    private final Connection _conn;
    private final Statement _stat;
    private final ResultSet _res;
    private boolean _isClosed = false;

    private LoanDataExtract(final DataSource source_) throws SQLException
    {
        _conn = source_.getConnection();
        _stat = _conn.createStatement();
        _res = _stat.executeQuery(SQL_STRING);
    }

    /**
     * Extract data from the DB. We will take ever strideSize_'th loan, up to
     * loanCount_ loans total. For each loan, extract all the data, and return
     * the dataset.
     *
     * To get the whole dataset, set strideSize_ = 1 and loanCount_ =
     * Integer.MAX_INT. This would take a lot of RAM, maybe several TB.
     *
     * @param loanCount_ The total number of loans to extract.
     * @param strideSize_ The number of loans between extracted loans.
     * @param startingStatus_
     * @throws SQLException
     */
    public void extractData(final int loanCount_, final int strideSize_, final LoanStatus startingStatus_) throws SQLException
    {

    }

    public RawDataTable<GseLoanField> extractBlock(final int blockSize_)
    {
        if (this.isClosed())
        {
            throw new IllegalStateException("This connection is closed.");
        }

        throw new UnsupportedOperationException("Not supported.");
    }

    public boolean isClosed()
    {
        return _isClosed;
    }

    @Override
    public void close() throws Exception
    {
        if (_isClosed)
        {
            return;
        }

        _isClosed = true;

        try
        {
            _res.close();
        }
        finally
        {
            try
            {
                _stat.close();
            }
            finally
            {
                _conn.close();
            }
        }

    }

}
