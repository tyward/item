

CREATE TABLE sfLoan (
    sfLoanId BIGSERIAL PRIMARY KEY,
    sourceLoanId CHAR(20) NOT NULL,
    sourceId CHAR(4) NOT NULL,
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
    purchase BOOLEAN NOT NULL,
    cashout BOOLEAN NOT NULL,
    origTerm INTEGER,
    numBorrowers INTEGER,
    firstTimeHomebuyer BOOLEAN,
    penalty BOOLEAN,
    seller VARCHAR(30),
    servicer VARCHAR(30),
    zip INTEGER,
    UNIQUE(sourceLoanId, sourceId)
);

CREATE UNIQUE INDEX CONCURRENTLY idx_sfLoan_sourcLoan ON sfLoan (sourceId, sourceLoanId)

CREATE TABLE sfLoanMonth (
    sfLoanId BIGINT NOT NULL,
    reportingDate DATE NOT NULL,
    balance DOUBLE PRECISION NOT NULL,
    status SMALLINT NOT NULL,
    age CHAR(1) NOT NULL,
    isPrepaid BOOLEAN NOT NULL,
    isDefaulted BOOLEAN NOT NULL,
    isModified BOOLEAN NOT NULL,
    FOREIGN KEY (sfLoanId) REFERENCES sfLoan (sfLoanId),
    PRIMARY KEY (sfLoanId, reportingDate)
    )

CREATE UNIQUE INDEX CONCURRENTLY idx_sfLoanMonth_dateLoan ON sfLoanMonth (reportingDate, sfLoanId)

CREATE TABLE sfLoanLiquidation (
    sfLoanId BIGINT NOT NULL,
    reportingDate DATE NOT NULL,
    fclCosts DOUBLE PRECISION,
    propertyCosts DOUBLE PRECISION,
    recoveryCosts DOUBLE PRECISION, 
    miscCOsts DOUBLE PRECISION,
    taxes DOUBLE PRECISION,
    netSaleProceeds DOUBLE PRECISION, 
    creditEnhancementProceeds DOUBLE PRECISION, 
    repurchaseProceeds DOUBLE PRECISION,
    otherFclProceeds DOUBLE PRECISION, 
    FOREIGN KEY (sfLoanId) REFERENCES sfLoan (sfLoanId),
    PRIMARY KEY (sfLoanId, reportingDate)
    );

CREATE UNIQUE INDEX CONCURRENTLY idx_sfLoanLiquidation_dateLoan ON sfLoanLiquidation (reportingDate, sfLoanId)

CREATE TABLE sfFileLoad (
    sfFileLoadId BIGSERIAL PRIMARY KEY,
    fileName VARCHAR(255) NOT NULL,
    reportingDate DATE NOT NULL,
    UNIQUE (fileName, reportingDate)
    )



