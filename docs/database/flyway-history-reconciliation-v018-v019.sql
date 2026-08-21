-- ShipFlow V018/V019 Flyway history reconciliation (MANUAL REVIEW ONLY)
--
-- This is an audited execution package, not an instruction to execute now.
-- It has not been connected to or executed against production.
--
-- Business-data rule:
--   The only persistent business-data INSERT in this package is into
--   flyway_schema_history. V018/V019 migration bodies are not repeated.
--   The procedure wrapper is administrative scaffolding for SIGNAL/rollback;
--   it must be created only in the reviewed DBA session and removed afterward.
--   No business table is created, altered, deleted, or updated.
--
-- Expected source files and Flyway 11.7.2 checksums:
--   V018__add_order_price_confirmation_scope.sql       1587549683
--   V019__add_exception_processing_evidence.sql        -85667777
--
-- Expected order:
--   installed_rank 7 -> version 018 -> V018
--   installed_rank 8 -> version 019 -> V019

-- ============================================================================
-- 1. EXECUTION-BEFORE BACKUP / READ-ONLY SNAPSHOT
-- ============================================================================
-- Run these statements first and save the result outside the database.
-- The DBA should also take the approved physical/logical backup before the
-- history write. Example command (review paths and client first):
-- mysqldump.exe --protocol=TCP --host=<host> --port=<port> --user=<user> \
--   --single-transaction --skip-lock-tables --no-create-info \
--   shipflow flyway_schema_history > flyway_schema_history_before_v018_v019.sql
--
-- The command above is intentionally a comment and has not been executed.

SELECT DATABASE() AS database_name, VERSION() AS mysql_version;

SHOW CREATE TABLE flyway_schema_history;

SELECT installed_rank, version, description, type, script, checksum, installed_by,
       installed_on, execution_time, success
FROM flyway_schema_history
ORDER BY installed_rank;

SELECT TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'flyway_schema_history'
ORDER BY ORDINAL_POSITION;

-- Target registration constants. These are review values, not a file checksum
-- query against the database. Checksum must be compared with the original
-- manually executed migration files or execution evidence before proceeding.
SELECT '018' AS version,
       'add order price confirmation scope' AS description,
       'V018__add_order_price_confirmation_scope.sql' AS script,
       1587549683 AS expected_checksum
UNION ALL
SELECT '019',
       'add exception processing evidence',
       'V019__add_exception_processing_evidence.sql',
       -85667777;

-- ============================================================================
-- 2. PRE-CHECK GATES
-- ============================================================================
-- All three values below must be PASS. Stop without starting the transaction
-- if any value is not PASS.
SELECT
    CASE WHEN DATABASE() = 'shipflow' THEN 'PASS' ELSE 'STOP' END AS database_gate,
    CASE WHEN (SELECT COUNT(*)
               FROM flyway_schema_history
               WHERE version IN ('018', '019')) = 0
         THEN 'PASS' ELSE 'STOP' END AS target_absence_gate,
    CASE WHEN (SELECT MAX(installed_rank) FROM flyway_schema_history) = 6
              AND (SELECT COUNT(*)
                   FROM flyway_schema_history
                   WHERE installed_rank = 6
                     AND version = '017'
                     AND success = 1) = 1
         THEN 'PASS' ELSE 'STOP' END AS predecessor_gate;

-- Existing target rows, including their checksums, must be empty. A checksum
-- mismatch is never repaired by this package; it is a hard stop.
SELECT installed_rank, version, description, type, script, checksum, success
FROM flyway_schema_history
WHERE version IN ('018', '019')
ORDER BY installed_rank;

-- ============================================================================
-- 3. REVIEWED TRANSACTION PROCEDURE
-- ============================================================================
-- MySQL SIGNAL is valid inside a stored program, not as a top-level SQL
-- statement. The following wrapper is therefore an equivalent manual plan.
-- It must be created only after the pre-check output is reviewed.
-- It leaves a successful transaction open for the DBA's explicit commit gate.

DELIMITER $$

DROP PROCEDURE IF EXISTS shipflow_reconcile_v018_v019_manual $$

CREATE PROCEDURE shipflow_reconcile_v018_v019_manual()
main: BEGIN
    DECLARE v_rows INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    IF DATABASE() <> 'shipflow' THEN
        SIGNAL SQLSTATE '45000'
            SET MYSQL_ERRNO = 45001,
                MESSAGE_TEXT = 'STOP: database is not shipflow';
    END IF;

    IF (SELECT COUNT(*)
        FROM flyway_schema_history
        WHERE version IN ('018', '019')) <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MYSQL_ERRNO = 45002,
                MESSAGE_TEXT = 'STOP: V018 or V019 already exists; do not repeat or repair';
    END IF;

    IF EXISTS (SELECT 1
               FROM flyway_schema_history
               WHERE (version = '018' AND (checksum IS NULL OR checksum <> 1587549683))
                  OR (version = '019' AND (checksum IS NULL OR checksum <> -85667777))) THEN
        SIGNAL SQLSTATE '45000'
            SET MYSQL_ERRNO = 45006,
                MESSAGE_TEXT = 'STOP: V018/V019 checksum mismatch; do not execute';
    END IF;

    IF (SELECT MAX(installed_rank) FROM flyway_schema_history) <> 6
       OR (SELECT COUNT(*)
           FROM flyway_schema_history
           WHERE installed_rank = 6
             AND version = '017'
             AND success = 1) <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MYSQL_ERRNO = 45003,
                MESSAGE_TEXT = 'STOP: latest successful Flyway version is not V017';
    END IF;

    -- Constants must match the manually executed source files. Never change
    -- either checksum to force a registration.
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
            SET MYSQL_ERRNO = 45004,
                MESSAGE_TEXT = 'STOP: history registration did not insert exactly two rows';
    END IF;

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
            SET MYSQL_ERRNO = 45005,
                MESSAGE_TEXT = 'STOP: V018/V019 metadata or checksum verification failed';
    END IF;

    SELECT installed_rank, version, description, type, script, checksum,
           installed_by, installed_on, execution_time, success
    FROM flyway_schema_history
    WHERE version IN ('018', '019')
    ORDER BY installed_rank;

    -- No COMMIT here. The DBA must inspect the result and explicitly COMMIT,
    -- or issue ROLLBACK in this same session.
END $$

DELIMITER ;

CALL shipflow_reconcile_v018_v019_manual();

-- ============================================================================
-- 4. EXECUTION-AFTER VERIFICATION
-- ============================================================================
-- Run in the same session before the manual commit decision. Required result:
-- registered_count=2, v018_valid_count=1, v019_valid_count=1.
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

-- ============================================================================
-- 5. EXPLICIT MANUAL COMMIT / CLEANUP GATE
-- ============================================================================
-- Do not execute these automatically. In the same reviewed session:  
--   COMMIT;
-- only when all three post-check values are correct. Otherwise:  
--   ROLLBACK;
-- and stop. After the transaction decision, remove the administrative wrapper:  
--   DROP PROCEDURE shipflow_reconcile_v018_v019_manual;
-- These lines are intentionally comments in this review package.

-- ============================================================================
-- 6. ROLLBACK NOTE
-- ============================================================================
-- If a committed registration later proves wrong, do not rerun V018/V019 and do
-- not DROP/ALTER business structures. First obtain written approval, verify the
-- backup, then prepare a separate history-only rollback for the exact rows whose
-- version, rank, script, checksum, and success values match this package.
-- Re-run the read-only post-check after that separate operation.
