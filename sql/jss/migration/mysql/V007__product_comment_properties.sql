-- Forward repair: keep V003 immutable and preserve existing comments.
-- The current ProductCommentDO persists List<ProductSkuDO.Property> through Jackson.
ALTER TABLE product_comment ADD COLUMN sku_properties JSON NULL AFTER sku_pic_url;
