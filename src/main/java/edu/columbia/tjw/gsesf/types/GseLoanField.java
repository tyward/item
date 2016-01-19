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
import edu.columbia.tjw.item.util.EnumMember;

/**
 *
 * @author tyler
 */
public enum GseLoanField implements EnumMember<GseLoanField>
{
    CREDIT_SCORE(GseType.INT),
    FIRST_PAYMENT_DATE(GseType.DATE),
    FIRST_TIME_BUYER(GseType.BOOLEAN),
    MATURITY_DATE(GseType.DATE),
    MSA(GseType.INT),
    MI_PERCENT(GseType.DOUBLE),
    UNIT_COUNT(GseType.INT),
    OCCUPANCY_STATUS(GseType.STRING),
    ORIG_CLTV(GseType.DOUBLE),
    ORIG_DTI(GseType.DOUBLE),
    ORIG_UPB(GseType.DOUBLE),
    ORIG_LTV(GseType.DOUBLE),
    ORIG_INTRATE(GseType.DOUBLE),
    CHANNEL(GseType.STRING),
    PREPAYMENT_PENALTY(GseType.BOOLEAN),
    PRODUCT_TYPE(GseType.STRING),
    PROPERTY_STATE(GseType.STRING),
    PROPERTY_TYPE(GseType.STRING),
    POSTAL_CODE(GseType.INT),
    LOAN_SEQUENCE_NUMBER(GseType.STRING),
    LOAN_PURPOSE(GseType.STRING),
    ORIG_TERM(GseType.INT),
    NUM_BORROWERS(GseType.INT),
    SELLER_NAME(GseType.STRING),
    SERVICER_NAME(GseType.STRING),
    FACTOR_DATE(GseType.DATE),
    UPB(GseType.DOUBLE),
    STATUS(GseType.STRING),
    REM_TERM(GseType.INT),
    REPURCHASE_FLAG(GseType.BOOLEAN),
    MOD_FLAG(GseType.BOOLEAN),
    ZERO_BAL_CODE(GseType.INT),
    ZERO_BAL_DATE(GseType.INT),
    INTRATE(GseType.DOUBLE),
    CURR_DEFERRED_UPB(GseType.DOUBLE),
    PAID_THROUGH_DATE(GseType.DATE),
    MI_RECOVERY(GseType.DOUBLE),
    NET_SALES_PROCEEDS(GseType.DOUBLE),
    NON_MI_RECOVERY(GseType.DOUBLE),
    EXPENSES(GseType.DOUBLE),
    AGE(GseType.INT),
    IS_MODIFIED(GseType.BOOLEAN);

    public static final EnumFamily<GseLoanField> FAMILY = new EnumFamily<>(values());

    private final GseType _type;

    private GseLoanField(final GseType type_)
    {
        _type = type_;
    }

    public GseType getType()
    {
        return _type;
    }

    @Override
    public EnumFamily<GseLoanField> getFamily()
    {
        return FAMILY;
    }

}
