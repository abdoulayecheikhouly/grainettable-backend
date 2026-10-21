CREATE TABLE product_image (
 product_id BIGINT NOT NULL REFERENCES product(id) ON DELETE CASCADE,
 position INTEGER NOT NULL CHECK (position >= 0 AND position < 8),
 image VARCHAR(255) NOT NULL,
 PRIMARY KEY(product_id, position)
);
INSERT INTO product_image(product_id,position,image) SELECT id,0,image FROM product;
CREATE TABLE product_rating (
 product_id BIGINT NOT NULL REFERENCES product(id) ON DELETE CASCADE,
 visitor_id VARCHAR(36) NOT NULL,
 score INTEGER NOT NULL CHECK (score BETWEEN 1 AND 5),
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(product_id,visitor_id)
);
