-- =====================================================================
-- AppUser table for the Java login screen (Group 7, Task 7/8).
-- Run AFTER sql/00_AirportOperation_dataset.sql, in a NEW SQL tab.
-- NOTE: the dataset script does DROP DATABASE, so run this file again
--       every time you reload the dataset.
-- Passwords are stored as SHA-256 hex (MySQL SHA2(...,256)); the Java app
-- hashes the typed password the same way and compares the two hashes.
-- =====================================================================
USE airport_operations;

-- Table exactly as given by the team leader
CREATE TABLE IF NOT EXISTS AppUser (
    UserID INT AUTO_INCREMENT PRIMARY KEY,
    Username VARCHAR(50) NOT NULL UNIQUE,
    PasswordHash VARCHAR(255) NOT NULL
);

-- Demo accounts (INSERT IGNORE = safe to run more than once)
INSERT IGNORE INTO AppUser (Username, PasswordHash) VALUES
    ('admin', SHA2('admin123', 256)),
    ('staff', SHA2('staff123', 256));

-- Add your own account like this:
-- INSERT INTO AppUser (Username, PasswordHash) VALUES ('loi', SHA2('your_password', 256));

SELECT UserID, Username, LEFT(PasswordHash, 16) AS HashPreview FROM AppUser;
