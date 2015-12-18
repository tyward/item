
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
    reportingDate DATE NOT NULL,
    UNIQUE (fileName, reportingDate)
    );




CREATE TABLE sfLoan (
    sfLoanId BIGSERIAL PRIMARY KEY,
    sourceLoanId CHAR(20) NOT NULL,
    sfSourceId INT NOT NULL,
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
    zip INTEGER,
    FOREIGN KEY (sfSellerId) REFERENCES sfSeller (sfSellerId),
    FOREIGN KEY (sfServicerId) REFERENCES sfServicer (sfServicerId),
    FOREIGN KEY (sfSourceId) REFERENCES sfSource (sfSourceId),
    UNIQUE(sourceLoanId, sfSourceId),
    UNIQUE(sfSourceId, sourceLoanId)
);


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
    PRIMARY KEY (sfLoanId, reportingDate),
    UNIQUE(reportingDate, sfLoanId)
    );


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
    PRIMARY KEY (sfLoanId, reportingDate),
    UNIQUE(reportingDate, sfLoanId)
    );

