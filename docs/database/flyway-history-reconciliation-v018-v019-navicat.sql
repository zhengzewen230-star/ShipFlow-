-- ShipFlow V018/V019 Flyway history reconciliation for Navicat
-- MANUAL REVIEW ONLY: this file is not connected to or executed by this run.
-- Target database: shipflow
-- Target table: flyway_schema_history
-- This package records history only; it does not replay V018/V019 migration SQL.
-- No business table is changed.
--
-- Source migration metadata calculated with Flyway 11.7.2-compatible CRC-32:
-- V018__add_order_price_confirmation_scope.sql | version 018 | rank 7
-- description: add order price confirmation scope
-- checksum: 1587549683
-- V019__add_exception_processing_evidence.sql | version 019 | rank 8
-- description: add exception processing evidence
-- checksum: -85667777

-- ============================================================================
-- 1. READ-ONLY EXECUTION-BEFORE CHECKS AND BACKUP SNAPSHOT
-- ============================================================================
-- Perform an approved backup before any history write. Example command only:
-- mysqldump.exe --protocol=TCP --host=<host> --port=<port> --user=<user> --single-transaction --skip-lock-tables --no-create-info shipflow flyway_schema_history > flyway_schema_history_before_v018_v019.sql
-- The command above is not executed by this file or by this run.

SELECT DATABASE() AS database_name, VERSION() AS mysql_version;

SHOW CREATE TABLE flyway_schema_history;

SELECT TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, COLUMN_TYPE,
       IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'flyway_schema_history'
ORDER BY ORDINAL_POSITION;

SELECT installed_rank, version, description, type, script, checksum,
       installed_by, installed_on, execution_time, success
FROM flyway_schema_history
ORDER BY installed_rank;

-- Expected values from the original project migration files.
SELECT '018' AS version,
       'add order price confirmation scope' AS description,
       'V018__add_order_price_confirmation_scope.sql' AS script,
       1587549683 AS checksum
UNION ALL
SELECT '019',
       'add exception processing evidence',
       'V019__add_exception_processing_evidence.sql',
       -85667777;

-- Before the transaction, all results must be PASS:
-- database_gate=PASS, latest_v017_gate=PASS, target_absence_gate=PASS.
SELECT
    CASE WHEN DATABASE() = 'shipflow' THEN 'PASS' ELSE 'STOP' END AS database_gate,
    CASE WHEN (SELECT MAX(installed_rank)
               FROM flyway_schema_history) = 6
              AND (SELECT COUNT(*)
                   FROM flyway_schema_history
                   WHERE installed_rank = 6
                     AND version = '017'
                     AND success = 1) = 1
         THEN 'PASS' ELSE 'STOP' END AS latest_v017_gate,
    CASE WHEN (SELECT COUNT(*)
               FROM flyway_schema_history
               WHERE version IN ('018', '019')) = 0
         THEN 'PASS' ELSE 'STOP' END AS target_absence_gate;

-- Any existing target row is a stop condition. Do not repeat either version.
SELECT installed_rank, version, description, type, script, checksum, success
FROM flyway_schema_history
WHERE version IN ('018', '019')
ORDER BY installed_rank;

-- ============================================================================
-- 2. MANUAL TRANSACTION WITH SIGNAL AND ROLLBACK
-- ============================================================================
-- MySQL requires SIGNAL inside a stored program. Navicat can send this block
-- after the read-only checks have been reviewed. The helper name is unique to
-- this reconciliation package. It is administrative scaffolding only; the
-- only business-table write in the procedure is the history INSERT below.
-- The procedure intentionally contains no COMMIT. The caller must review the
-- post-check and decide the transaction outcome manually.

DELIMITER $$

CREATE PROCEDURE shipflow_reconcile_v018_v019_navicat_manual()
BEGIN
    DECLARE v_rows INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    IF DATABASE() <> 'shipflow' THEN
        SIGNAL SQLSTATE '45000'
            SET MYSQL_ERRNO = 45101,
                MESSAGE_TEXT = 'STOP: current database is not shipflow';
    END IF;

    IF (SELECT MAX(installed_rank)
        FROM flyway_schema_history) <> 6
       OR (SELECT COUNT(*)
           FROM flyway_schema_history
           WHERE installed_rank = 6
             AND version = '017'
             AND success = 1) <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MYSQL_ERRNO = 45102,
                MESSAGE_TEXT = 'STOP: latest successful Flyway version is not V017';
    END IF;

    IF (SELECT COUNT(*)
        FROM flyway_schema_history
        WHERE version IN ('018', '019')) <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MYSQL_ERRNO = 45103,
                MESSAGE_TEXT = 'STOP: V018 or V019 already exists';
    END IF;

    -- These constants are the signed INT checksums computed from the project
    -- migration files. Never replace them with a value from an unverified file.
    INSERT INTO flyway_schema_history
        (installed_rank, version, description, type, script, checksum,
         installed_by, installed_on, execution_time, success)
    VALUES
        (7, '018', 'add order price confirmation scope', 'SQL',
         'V018__add_order_price_confirmation_scope.sql', 1587549683,
         CURRENT_USER(), CURRENT_TIMESTAMP, 0, 1),
        (8, '019', 'add exception processing evidence', 'SQL',
         'V019__add_exception_processing_evidence.sql', -85667777,
         CURRENT_USER(), CURRENT_TIMESTAMP, 0, 1);

    SET v_rows = ROW_COUNT();
    IF v_rows <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MYSQL_ERRNO = 45104,
                MESSAGE_TEXT = 'STOP: exactly two history rows were not inserted';
    END IF;

    -- A checksum mismatch immediately signals and the handler rolls back.
    IF (SELECT COUNT(*)
        FROM flyway_schema_history
        WHERE version = '018'
          AND installed_rank = 7
          AND description = 'add order price confirmation scope'
          AND type = 'SQL'
          AND script = 'V018__add_order_price_confirmation_scope.sql'
          AND checksum = 1587549683
          AND success = 1) <> 1
       OR (SELECT COUNT(*)
           FROM flyway_schema_history
           WHERE version = '019'
             AND installed_rank = 8
             AND description = 'add exception processing evidence'
             AND type = 'SQL'
             AND script = 'V019__add_exception_processing_evidence.sql'
             AND checksum = -85667777
             AND success = 1) <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MYSQL_ERRNO = 45105,
                MESSAGE_TEXT = 'STOP: V018/V019 checksum or metadata mismatch';
    END IF;

    SELECT installed_rank, version, description, type, script, checksum,
           installed_by, installed_on, execution_time, success
    FROM flyway_schema_history
    WHERE version IN ('018', '019')
    ORDER BY installed_rank;

    -- No automatic COMMIT. Review the result in this same session first.
END $$

DELIMITER ;

CALL shipflow_reconcile_v018_v019_navicat_manual();

-- ============================================================================
-- 3. EXECUTION-AFTER CHECKS
-- ============================================================================
SELECT installed_rank, version, description, type, script, checksum,
       installed_by, installed_on, execution_time, success
FROM flyway_schema_history
WHERE version IN ('018', '019')
ORDER BY installed_rank;

SELECT
    (SELECT COUNT(*)
     FROM flyway_schema_history
     WHERE version IN ('018', '019')) AS registered_count,
    (SELECT COUNT(*)
     FROM flyway_schema_history
     WHERE version = '018'
       AND installed_rank = 7
       AND description = 'add order price confirmation scope'
       AND type = 'SQL'
       AND script = 'V018__add_order_price_confirmation_scope.sql'
       AND checksum = 1587549683
       AND success = 1) AS v018_valid_count,
    (SELECT COUNT(*)
     FROM flyway_schema_history
     WHERE version = '019'
       AND installed_rank = 8
       AND description = 'add exception processing evidence'
       AND type = 'SQL'
       AND script = 'V019__add_exception_processing_evidence.sql'
       AND checksum = -85667777
       AND success = 1) AS v019_valid_count;

-- Required post-check result: registered_count=2,
-- v018_valid_count=1, v019_valid_count=1.
-- The package intentionally provides no COMMIT statement.
-- After manual review, the DBA must decide the transaction outcome in the
-- same session according to the approved runbook.
