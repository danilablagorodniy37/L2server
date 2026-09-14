CREATE TABLE IF NOT EXISTS `messages` (
  `messageId` INT NOT NULL DEFAULT 0,
  `senderId` INT NOT NULL DEFAULT 0,
  `receiverId` INT NOT NULL DEFAULT 0,
  `subject` TINYTEXT,
  `content` TEXT,
  `expiration` bigint(13) unsigned NOT NULL DEFAULT '0',
  `reqAdena` BIGINT NOT NULL DEFAULT 0,
  `hasAttachments` BOOL DEFAULT FALSE NOT NULL,
  `isUnread` BOOL DEFAULT TRUE NOT NULL,
  `isDeletedBySender` BOOL DEFAULT FALSE NOT NULL,
  `isDeletedByReceiver` BOOL DEFAULT FALSE NOT NULL,
  `sendBySystem` tinyint(1) NOT NULL DEFAULT 0,
  `isReturned` BOOL DEFAULT FALSE NOT NULL,
  PRIMARY KEY (`messageId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;