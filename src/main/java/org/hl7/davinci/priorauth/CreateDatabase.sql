BEGIN TRANSACTION;

    CREATE TABLE IF NOT EXISTS Bundle (
        "id" varchar PRIMARY KEY,
        "patient" varchar,
        "timestamp" datetime DEFAULT CURRENT_TIMESTAMP,
        "resource" clob
    );

    CREATE TABLE IF NOT EXISTS Claim (
        "id" varchar PRIMARY KEY,
        "patient" varchar,
        "related" varchar DEFAULT NULL,
        "status" varchar,
        "isDifferential" boolean DEFAULT FALSE,
        "timestamp" datetime DEFAULT CURRENT_TIMESTAMP,
        "resource" clob,
        FOREIGN KEY ("related") REFERENCES Claim("id") ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS ClaimResponse (
        "id" varchar PRIMARY KEY,
        "claimId" varchar,
        "patient" varchar,
        "status" varchar,
        "outcome" varchar,
        "isDifferential" boolean DEFAULT FALSE,
        "timestamp" datetime DEFAULT CURRENT_TIMESTAMP,
        "resource" clob,
        FOREIGN KEY ("claimId") REFERENCES Claim("id") ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS ClaimItem (
        "id" varchar,
        "sequence" varchar,
        "outcome" varchar DEFAULT NULL,
        "status" varchar,
        "timestamp" datetime DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT pk_claimitems PRIMARY KEY ("id", "sequence"),
        FOREIGN KEY ("id") REFERENCES Claim("id") ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS Subscription (
        "id" varchar PRIMARY KEY,
        "claimResponseId" varchar,
        "patient" varchar,
        "status" varchar,
        "end" varchar,
        "websocketId" varchar DEFAULT NULL,
        "timestamp" datetime DEFAULT CURRENT_TIMESTAMP,
        "resource" clob,
        FOREIGN KEY ("claimResponseId") REFERENCES ClaimResponse("id") 
    );

COMMIT;