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
 *//**
 * Author:  tyler
 * Created: Sep 25, 2016
 */



DROP TABLE sfLoanLiquidation;
DROP TABLE sfLoanMonth;
DROP TABLE sfLoanMonthStaging;
DROP TABLE sfLoan;
DROP TABLE sfFileLoad;
DROP TABLE sfServicer;
DROP TABLE sfSeller;
DROP TABLE sfSource;

CREATE TABLE sfSource (
    sfSourceId INTEGER PRIMARY KEY,
    sfSourceName VARCHAR(32) NOT NULL,
    UNIQUE(sfSourceName)
    );

INSERT INTO sfSource (sfSourceId, sfSourceName) VALUES (0, 'Fannie Mae');
INSERT INTO sfSource (sfSourceId, sfSourceName) VALUES (1, 'Freddie Mac');


CREATE TABLE sfSeller (
    sfSellerId SERIAL PRIMARY KEY,
    sellerName VARCHAR(64) NOT NULL,
    UNIQUE(sellerName)
);

CREATE TABLE sfServicer (
    sfServicerId SERIAL PRIMARY KEY,
    servicerName VARCHAR(64) NOT NULL,
    UNIQUE(servicerName)
);

CREATE TABLE sfFileLoad (
    sfFileLoadId BIGSERIAL PRIMARY KEY,
    fileName VARCHAR(255) NOT NULL,
    fileEntry VARCHAR(255) NOT NULL,
    loadDate TIMESTAMP NOT NULL DEFAULT clock_timestamp(),
    loadComplete TIMESTAMP,
    UNIQUE (fileName, fileEntry)
    );

CREATE TABLE sfLoan (
    sfSourceId INT NOT NULL,
    sfLoanId BIGSERIAL NOT NULL,
    sfFileLoadId BIGSERIAL NOT NULL,
    sourceLoanId CHAR(20) NOT NULL,
    fico INTEGER,
    firstPaymentDate DATE,
    maturityDate DATE, 
    msa INTEGER, 
    miPercent DOUBLE PRECISION,
    numUnits INTEGER,
    cltv DOUBLE PRECISION,
    dti DOUBLE PRECISION, 
    upb DOUBLE PRECISION,
    ltv DOUBLE PRECISION,
    initrate DOUBLE PRECISION,
    channel char(1),
    occupancy char(1),
    productType CHAR(5),
    propertyState CHAR(2),
    propertyType CHAR(2),
    zipCode INTEGER,
    purpose CHAR(1) NOT NULL,
    origTerm INTEGER,
    numBorrowers INTEGER,
    firstTimeHomebuyer BOOLEAN,
    penalty BOOLEAN,
    sfSellerId INTEGER,
    sfServicerId INTEGER,
    PRIMARY KEY (sfSourceId, sfLoanId),
    FOREIGN KEY (sfFileLoadId) REFERENCES sfFileLoad (sfFileLoadId),
    FOREIGN KEY (sfSellerId) REFERENCES sfSeller (sfSellerId),
    FOREIGN KEY (sfServicerId) REFERENCES sfServicer (sfServicerId),
    FOREIGN KEY (sfSourceId) REFERENCES sfSource (sfSourceId),
    UNIQUE(sourceLoanId, sfSourceId),
    UNIQUE(sfSourceId, sourceLoanId),
    UNIQUE(sfFileLoadId, sourceLoanId)
);



CREATE TABLE sfLoanMonth (
    sfSourceId INT NOT NULL,
    sfLoanId BIGINT NOT NULL,
    sfFileLoadId BIGSERIAL NOT NULL,
    reportingDate DATE NOT NULL,
    balance DOUBLE PRECISION NOT NULL,
    status VARCHAR(3) NOT NULL,
    age SMALLINT NOT NULL,
    isPrepaid BOOLEAN NOT NULL,
    isDefaulted BOOLEAN NOT NULL,
    isModified BOOLEAN NOT NULL,
    FOREIGN KEY (sfSourceId, sfLoanId) REFERENCES sfLoan (sfSourceId, sfLoanId),
    FOREIGN KEY (sfFileLoadId) REFERENCES sfFileLoad (sfFileLoadId),
    PRIMARY KEY (sfSourceId, sfLoanId, reportingDate),
    UNIQUE(sfSourceId, reportingDate, sfLoanId),
    UNIQUE(sfFileLoadId, reportingDate, sfLoanId)
    );

CREATE INDEX idx_sfLoanMonthFileLoad ON sfLoanMonth (sfFileLoadId);

CREATE TABLE sfLoanMonthStaging (
    stagingId CHAR(32) NOT NULL,
    sfSourceId INT NOT NULL,
    sourceLoanId CHAR(20) NOT NULL,
    reportingDate DATE NOT NULL,
    balance DOUBLE PRECISION NOT NULL,
    status VARCHAR(3) NOT NULL,
    age SMALLINT NOT NULL,
    isPrepaid BOOLEAN NOT NULL,
    isDefaulted BOOLEAN NOT NULL,
    isModified BOOLEAN NOT NULL,
    PRIMARY KEY (stagingId, sfSourceId, sourceLoanId, reportingDate)
    );




CREATE TABLE sfLoanLiquidation (
    sfSourceId INT NOT NULL,
    sfLoanId BIGINT NOT NULL,
    reportingDate DATE NOT NULL,
    fclCosts DOUBLE PRECISION,
    propertyCosts DOUBLE PRECISION,
    recoveryCosts DOUBLE PRECISION, 
    miscCosts DOUBLE PRECISION,
    taxes DOUBLE PRECISION,
    netSaleProceeds DOUBLE PRECISION, 
    creditEnhancementProceeds DOUBLE PRECISION, 
    repurchaseProceeds DOUBLE PRECISION,
    otherFclProceeds DOUBLE PRECISION, 
    FOREIGN KEY (sfSourceId, sfLoanId) REFERENCES sfLoan (sfSourceId, sfLoanId),
    PRIMARY KEY (sfLoanId, reportingDate),
    UNIQUE(reportingDate, sfLoanId)
    );

