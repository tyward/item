
DROP TABLE sfLoanLiquidation;
DROP TABLE sfLoanMonth;
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
    sfSellerId BIGINT PRIMARY KEY,
    sellerName VARCHAR(64) NOT NULL,
    UNIQUE(sellerName)
);

CREATE TABLE sfServicer (
    sfServicerId BIGINT PRIMARY KEY,
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
    sfLoanId BIGINT NOT NULL,
    sfLoanChecksum BIGINT NOT NULL,
    sfRecordStart TIMESTAMP NOT NULL DEFAULT NOW(),
    sfRecordEnd TIMESTAMP CHECK (sfRecordEnd IS NULL OR sfRecordEnd > sfRecordStart),
    sfFileLoadId BIGINT NOT NULL,
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
    sfSellerId BIGINT,
    sfServicerId BIGINT,
    PRIMARY KEY (sfSourceId, sfLoanId, sfLoanChecksum),
    FOREIGN KEY (sfFileLoadId) REFERENCES sfFileLoad (sfFileLoadId),
    FOREIGN KEY (sfSellerId) REFERENCES sfSeller (sfSellerId),
    FOREIGN KEY (sfServicerId) REFERENCES sfServicer (sfServicerId),
    FOREIGN KEY (sfSourceId) REFERENCES sfSource (sfSourceId),
    UNIQUE(sourceLoanId, sfSourceId, sfRecordStart)
);



CREATE TABLE sfLoanMonth (
    sfSourceId INT NOT NULL,
    sfLoanId BIGINT NOT NULL,
    sfLoanChecksum BIGINT,
    sfLoanMonthChecksum BIGINT NOT NULL,
    sfRecordStart TIMESTAMP NOT NULL DEFAULT NOW(),
    sfRecordEnd TIMESTAMP CHECK (sfRecordEnd IS NULL OR sfRecordEnd > sfRecordStart),
    sfFileLoadId BIGSERIAL NOT NULL,
    reportingDate DATE NOT NULL,
    balance DOUBLE PRECISION NOT NULL,
    status VARCHAR(3) NOT NULL,
    age SMALLINT NOT NULL,
    isPrepaid BOOLEAN NOT NULL,
    isDefaulted BOOLEAN NOT NULL,
    isModified BOOLEAN NOT NULL,
    FOREIGN KEY (sfSourceId, sfLoanId, sfLoanChecksum) REFERENCES sfLoan (sfSourceId, sfLoanId, sfLoanChecksum),
    FOREIGN KEY (sfFileLoadId) REFERENCES sfFileLoad (sfFileLoadId),
    PRIMARY KEY (sfSourceId, sfLoanId, reportingDate, sfLoanMonthChecksum),
    UNIQUE(sfSourceId, sfLoanId, reportingDate, sfLoanMonthChecksum, sfRecordStart)
    );



CREATE TABLE sfLoanLiquidation (
    sfSourceId INT NOT NULL,
    sfLoanId BIGINT NOT NULL,
    sfLoanChecksum BIGINT NOT NULL,
    sfLoanLiquidationChecksum BIGINT NOT NULL,
    sfRecordStart TIMESTAMP NOT NULL DEFAULT NOW(),
    sfRecordEnd TIMESTAMP CHECK (sfRecordEnd IS NULL OR sfRecordEnd > sfRecordStart),
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
    FOREIGN KEY (sfSourceId, sfLoanId, sfLoanChecksum) REFERENCES sfLoan (sfSourceId, sfLoanId, sfLoanChecksum),
    PRIMARY KEY (sfLoanId, reportingDate),
    UNIQUE(sfSourceId, sfLoanId, sfLoanLiquidationChecksum, sfRecordStart)
    );
