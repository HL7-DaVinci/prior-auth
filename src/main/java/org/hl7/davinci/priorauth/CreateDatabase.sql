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
        "provider" varchar,
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
        "patient" varchar DEFAULT NULL,
        "orgId" varchar,
        "status" varchar,
        "end" varchar,
        "websocketId" varchar DEFAULT NULL,
        "timestamp" datetime DEFAULT CURRENT_TIMESTAMP,
        "resource" clob
    );

    CREATE TABLE IF NOT EXISTS Rules (
        "system" varchar,
        "code" varchar,
        "topic" varchar,
        "rule" varchar,
        "timestamp" datetime DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT pk_rules PRIMARY KEY ("system", "code")
    );

    CREATE TABLE IF NOT EXISTS Audit (
        "id" varchar,
        "type" varchar,
        "action" varchar,
        "outcome" varchar,
        "what" varchar,
        "query" varchar,
        "ip" varchar,
        "timestamp" datetime DEFAULT CURRENT_TIMESTAMP,
        "resource" clob
    );

    CREATE TABLE IF NOT EXISTS Client (
        "id" varchar PRIMARY KEY,
        "jwks" varchar DEFAULT NULL,
        "jwks_url" varchar DEFAULT NULL,
        "token" varchar DEFAULT NULL,
        "timestamp" datetime DEFAULT CURRENT_TIMESTAMP,
        "organization" clob
    );

COMMIT;