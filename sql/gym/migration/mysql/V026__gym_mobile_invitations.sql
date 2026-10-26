-- First-time verified invitation attribution; no historical code validation or reward grants.
CREATE TABLE IF NOT EXISTS gym_mobile_invitation (
 id bigint NOT NULL AUTO_INCREMENT,
 store_id bigint NOT NULL,
 inviter_member_id bigint NOT NULL,
 inviter_user_id bigint NOT NULL,
 invite_code char(32) NOT NULL,
 share_page varchar(200) NOT NULL,
 issued_at datetime(6) NOT NULL,
 expires_at datetime(6) NOT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted bit(1) NOT NULL DEFAULT b'0',
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_mobile_invitation_code (tenant_id,invite_code),
 KEY idx_mobile_invitation_owner (tenant_id,inviter_member_id,store_id,deleted,id),
 CONSTRAINT ck_mobile_invitation_expiry CHECK (expires_at>issued_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE gym_mobile_identity
 ADD COLUMN invitation_id bigint DEFAULT NULL,
 ADD COLUMN inviter_member_id bigint DEFAULT NULL,
 ADD COLUMN inviter_user_id bigint DEFAULT NULL,
 ADD COLUMN invitation_new_member bit(1) DEFAULT NULL,
 ADD KEY idx_mobile_identity_invitation (tenant_id,invitation_id),
 ADD CONSTRAINT ck_mobile_identity_invitation CHECK (
  (invitation_id IS NULL AND inviter_member_id IS NULL AND inviter_user_id IS NULL AND invitation_new_member IS NULL)
  OR (invitation_id IS NOT NULL AND inviter_member_id IS NOT NULL AND inviter_user_id IS NOT NULL AND invitation_new_member IS NOT NULL AND inviter_member_id<>member_id AND inviter_user_id<>user_id)
 );
