-- RegEx search/replace
-- ^([^;\r\n- ]+);([^;\r\n]+);([^;\r\n]+);([^;\r\n]+);([^;\r\n]+);([^;\r\n]+)
-- ALTER TABLE $1 RENAME COLUMN $2 TO old_$2;ALTER TABLE $1 ADD COLUMN $2 BOOL NOT NULL DEFAULT $3;UPDATE $1 SET $2=$4 WHERE CAST\(old_$2 AS CHAR\)='$5' OR CAST\(old_$2 AS CHAR\)='$6';ALTER TABLE $1 DROP COLUMN old_$2;
-- 
-- DataTable
-- table;column;default;other;other_cmp_s;other_cmp_i
-- castle;regTimeOver;TRUE;FALSE;false;0
-- castle;showNpcCrest;FALSE;TRUE;true;1
-- clanhall_siege_guards;isSiegeBoss;FALSE;TRUE;true;1
-- clan_notices;enabled;FALSE;TRUE;true;1
-- heroes;claimed;FALSE;TRUE;true;1
-- messages;hasAttachments;FALSE;TRUE;true;1
-- messages;isUnread;TRUE;FALSE;false;0
-- messages;isDeletedBySender;FALSE;TRUE;true;1
-- messages;isDeletedByReceiver;FALSE;TRUE;true;1
-- messages;isLocked;FALSE;TRUE;true;1
-- messages;isReturned;FALSE;TRUE;true;1
-- pets;restore;FALSE;TRUE;true;1
--
-- Notes
-- clanhall_siege_guards.isSiegeBoss: Not loaded/saved anywhere.
-- messages.isLocked: Not loaded/saved anywhere. Auto calculated value.

ALTER TABLE castle RENAME COLUMN regTimeOver TO old_regTimeOver;
ALTER TABLE castle ADD COLUMN regTimeOver BOOL NOT NULL DEFAULT TRUE;
UPDATE castle SET regTimeOver=FALSE WHERE CAST(old_regTimeOver AS CHAR)='false' OR CAST(old_regTimeOver AS CHAR)='0';
ALTER TABLE castle DROP COLUMN old_regTimeOver;

ALTER TABLE castle RENAME COLUMN showNpcCrest TO old_showNpcCrest;
ALTER TABLE castle ADD COLUMN showNpcCrest BOOL NOT NULL DEFAULT FALSE;
UPDATE castle SET showNpcCrest=TRUE WHERE CAST(old_showNpcCrest AS CHAR)='true' OR CAST(old_showNpcCrest AS CHAR)='1';
ALTER TABLE castle DROP COLUMN old_showNpcCrest;

-- clanhall_siege_guards.isSiegeBoss is never loaded/saved anywhere. But the value varies.
-- ALTER TABLE clanhall_siege_guards RENAME COLUMN isSiegeBoss TO old_isSiegeBoss;
-- ALTER TABLE clanhall_siege_guards ADD COLUMN isSiegeBoss BOOL NOT NULL DEFAULT FALSE;
-- UPDATE clanhall_siege_guards SET isSiegeBoss=TRUE WHERE CAST(old_isSiegeBoss AS CHAR)='true' OR CAST(old_isSiegeBoss AS CHAR)='1';
-- ALTER TABLE clanhall_siege_guards DROP COLUMN old_isSiegeBoss;
ALTER TABLE clanhall_siege_guards DROP COLUMN isSiegeBoss;

ALTER TABLE clan_notices RENAME COLUMN enabled TO old_enabled;
ALTER TABLE clan_notices ADD COLUMN enabled BOOL NOT NULL DEFAULT FALSE;
UPDATE clan_notices SET enabled=TRUE WHERE CAST(old_enabled AS CHAR)='true' OR CAST(old_enabled AS CHAR)='1';
ALTER TABLE clan_notices DROP COLUMN old_enabled;

ALTER TABLE heroes RENAME COLUMN claimed TO old_claimed;
ALTER TABLE heroes ADD COLUMN claimed BOOL NOT NULL DEFAULT FALSE;
UPDATE heroes SET claimed=TRUE WHERE CAST(old_claimed AS CHAR)='true' OR CAST(old_claimed AS CHAR)='1';
ALTER TABLE heroes DROP COLUMN old_claimed;

ALTER TABLE messages RENAME COLUMN hasAttachments TO old_hasAttachments;
ALTER TABLE messages ADD COLUMN hasAttachments BOOL NOT NULL DEFAULT FALSE;
UPDATE messages SET hasAttachments=TRUE WHERE CAST(old_hasAttachments AS CHAR)='true' OR CAST(old_hasAttachments AS CHAR)='1';
ALTER TABLE messages DROP COLUMN old_hasAttachments;

ALTER TABLE messages RENAME COLUMN isUnread TO old_isUnread;
ALTER TABLE messages ADD COLUMN isUnread BOOL NOT NULL DEFAULT TRUE;
UPDATE messages SET isUnread=FALSE WHERE CAST(old_isUnread AS CHAR)='false' OR CAST(old_isUnread AS CHAR)='0';
ALTER TABLE messages DROP COLUMN old_isUnread;

ALTER TABLE messages RENAME COLUMN isDeletedBySender TO old_isDeletedBySender;
ALTER TABLE messages ADD COLUMN isDeletedBySender BOOL NOT NULL DEFAULT FALSE;
UPDATE messages SET isDeletedBySender=TRUE WHERE CAST(old_isDeletedBySender AS CHAR)='true' OR CAST(old_isDeletedBySender AS CHAR)='1';
ALTER TABLE messages DROP COLUMN old_isDeletedBySender;

ALTER TABLE messages RENAME COLUMN isDeletedByReceiver TO old_isDeletedByReceiver;
ALTER TABLE messages ADD COLUMN isDeletedByReceiver BOOL NOT NULL DEFAULT FALSE;
UPDATE messages SET isDeletedByReceiver=TRUE WHERE CAST(old_isDeletedByReceiver AS CHAR)='true' OR CAST(old_isDeletedByReceiver AS CHAR)='1';
ALTER TABLE messages DROP COLUMN old_isDeletedByReceiver;

-- messages.isLocked is never loaded/saved anywhere.
-- ALTER TABLE messages RENAME COLUMN isLocked TO old_isLocked;
-- ALTER TABLE messages ADD COLUMN isLocked BOOL NOT NULL DEFAULT FALSE;
-- UPDATE messages SET isLocked=TRUE WHERE CAST(old_isLocked AS CHAR)='true' OR CAST(old_isLocked AS CHAR)='1';
-- ALTER TABLE messages DROP COLUMN old_isLocked;
ALTER TABLE messages DROP COLUMN isLocked;

ALTER TABLE messages RENAME COLUMN isReturned TO old_isReturned;
ALTER TABLE messages ADD COLUMN isReturned BOOL NOT NULL DEFAULT FALSE;
UPDATE messages SET isReturned=TRUE WHERE CAST(old_isReturned AS CHAR)='true' OR CAST(old_isReturned AS CHAR)='1';
ALTER TABLE messages DROP COLUMN old_isReturned;

ALTER TABLE pets RENAME COLUMN restore TO old_restore;
ALTER TABLE pets ADD COLUMN restore BOOL NOT NULL DEFAULT FALSE;
UPDATE pets SET restore=TRUE WHERE CAST(old_restore AS CHAR)='true' OR CAST(old_restore AS CHAR)='1';
ALTER TABLE pets DROP COLUMN old_restore;
