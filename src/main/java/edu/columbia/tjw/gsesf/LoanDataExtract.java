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
import edu.columbia.tjw.gsesf.types.RawDataType;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
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
            + " ORDER BY o.sfsourceId, o.sfLoanId, m.reportingDate";

    private static final int BLOCK_SIZE = 10 * 1000;
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
    private boolean _isClosed = false;

    public LoanDataExtract(final DataSource source_, final int maxRows_) throws SQLException
    {
        _conn = source_.getConnection();
        _stat = _conn.createStatement();

        _stat.setFetchSize(BLOCK_SIZE);
        _stat.setMaxRows(maxRows_);
    }

    /**
     * Extract data from the DB. We will take every strideSize_'th loan, up to
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
        _stat.setFetchSize(loanCount_);

        try (final ResultSet res = _stat.executeQuery(SQL_STRING))
        {
            final RawDataTable<GseLoanField> raw = extractBlock(1000, res);

            System.out.println("Ping.");
        }

    }

    /**
     * Some points to note. We will actually extract whole blocks from the DB,
     * and then discard what we don't need afterwards. There are a few reasons
     * for this.
     *
     * 1) We need to limit this to individual loans, which means grouping loans
     * together and then dropping some of them. Not quite so easy to do in SQL.
     *
     * 2) We need to limit this to specific starting status, also, not easy to
     * do on the DB, because now which rows get dropped depends on other row
     * values.
     *
     * @param blockSize_
     * @param res_
     * @return
     * @throws SQLException
     */
    private RawDataTable<GseLoanField> extractBlock(final int blockSize_, final ResultSet res_) throws SQLException
    {
        if (this.isClosed())
        {
            throw new IllegalStateException("This connection is closed.");
        }

        final RawDataTable<GseLoanField> raw = new RawDataTable<>(GseLoanField.FAMILY, blockSize_);

        while (res_.next())
        {
            if (!raw.appendRow())
            {
                throw new IllegalStateException("Cannot expand RawDataTable.");
            }

            for (int i = 0; i < FIELDS.size(); i++)
            {
                final GseLoanField next = FIELDS.get(i);

                if (null == next)
                {
                    //Nothing of interest here, skip to next one.
                    continue;
                }

                final RawDataType type = next.getType();

                switch (type)
                {
                    case DOUBLE:
                    {
                        final double val = res_.getDouble(1 + i);
                        raw.setDouble(next, val);
                        break;
                    }
                    case INT:
                    {
                        final int val = res_.getInt(1 + i);
                        raw.setInt(next, val);
                        break;
                    }
                    case STRING:
                    {
                        final String val = res_.getString(1 + i);
                        raw.setString(next, val);
                        break;
                    }
                    case BOOLEAN:
                    {
                        final boolean val = res_.getBoolean(1 + i);
                        raw.setBoolean(next, val);
                        break;
                    }
                    case DATE:
                    {
                        final Date val = res_.getDate(1 + i);
                        final LocalDate localDate = val.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                        raw.setDate(next, localDate);
                        break;
                    }
                    default:
                        throw new UnsupportedOperationException("Unsupported.");
                }
            }
        }

        return raw;
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
            _stat.close();
        }
        finally
        {
            _conn.close();
        }
    }

}
