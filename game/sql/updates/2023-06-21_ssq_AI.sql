ALTER TABLE `seven_signs`
ADD COLUMN `position` INT(1) NOT NULL DEFAULT 0 AFTER `seal`,
ADD COLUMN `paid_type` INT(1) NOT NULL DEFAULT 0 AFTER `position`;