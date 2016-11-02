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
package edu.columbia.tjw.gsesf.types;

import edu.columbia.tjw.item.util.EnumFamily;

/**
 *
 * @author tyler
 */
public enum GseLoanField implements TypedField<GseLoanField>
{
    CREDIT_SCORE(RawDataType.INT),
    FIRST_PAYMENT_DATE(RawDataType.DATE),
    FIRST_TIME_BUYER(RawDataType.BOOLEAN),
    MATURITY_DATE(RawDataType.DATE),
    MSA(RawDataType.INT),
    MI_PERCENT(RawDataType.DOUBLE),
    UNIT_COUNT(RawDataType.INT),
    OCCUPANCY_STATUS(RawDataType.STRING),
    ORIG_CLTV(RawDataType.DOUBLE),
    ORIG_DTI(RawDataType.DOUBLE),
    ORIG_UPB(RawDataType.DOUBLE),
    ORIG_LTV(RawDataType.DOUBLE),
    ORIG_INTRATE(RawDataType.DOUBLE),
    CHANNEL(RawDataType.STRING),
    PREPAYMENT_PENALTY(RawDataType.BOOLEAN),
    PRODUCT_TYPE(RawDataType.STRING),
    PROPERTY_STATE(RawDataType.STRING),
    PROPERTY_TYPE(RawDataType.STRING),
    POSTAL_CODE(RawDataType.INT),
    LOAN_SEQUENCE_NUMBER(RawDataType.STRING),
    LOAN_PURPOSE(RawDataType.STRING),
    ORIG_TERM(RawDataType.INT),
    NUM_BORROWERS(RawDataType.INT),
    SELLER_NAME(RawDataType.STRING),
    SERVICER_NAME(RawDataType.STRING),
    FACTOR_DATE(RawDataType.DATE),
    UPB(RawDataType.DOUBLE),
    STATUS(RawDataType.STRING),
    REM_TERM(RawDataType.INT),
    REPURCHASE_FLAG(RawDataType.BOOLEAN),
    MOD_FLAG(RawDataType.BOOLEAN),
    ZERO_BAL_CODE(RawDataType.INT),
    ZERO_BAL_DATE(RawDataType.INT),
    INTRATE(RawDataType.DOUBLE),
    CURR_DEFERRED_UPB(RawDataType.DOUBLE),
    PAID_THROUGH_DATE(RawDataType.DATE),
    MI_RECOVERY(RawDataType.DOUBLE),
    NET_SALES_PROCEEDS(RawDataType.DOUBLE),
    NON_MI_RECOVERY(RawDataType.DOUBLE),
    EXPENSES(RawDataType.DOUBLE),
    AGE(RawDataType.INT),
    IS_MODIFIED(RawDataType.BOOLEAN),
    SOURCE_NAME(RawDataType.STRING),
    IS_PREPAID(RawDataType.BOOLEAN),
    IS_DEFAULTED(RawDataType.BOOLEAN);

    public static final EnumFamily<GseLoanField> FAMILY = new EnumFamily<>(values());

    private final RawDataType _type;

    private GseLoanField(final RawDataType type_)
    {
        _type = type_;
    }

    public RawDataType getType()
    {
        return _type;
    }

    @Override
    public EnumFamily<GseLoanField> getFamily()
    {
        return FAMILY;
    }

}
