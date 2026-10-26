-- Historical equipment remains private until an authorized administrator publishes it.
ALTER TABLE gym_equipment ADD COLUMN public_scene varchar(32) DEFAULT NULL;
CREATE UNIQUE INDEX uk_equipment_public_scene ON gym_equipment (tenant_id, public_scene);
