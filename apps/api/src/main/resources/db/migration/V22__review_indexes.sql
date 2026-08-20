-- Module 11: Review index optimizations for filtering & sorting

CREATE INDEX IF NOT EXISTS idx_review_restaurant_created ON review(restaurant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_review_restaurant_rating ON review(restaurant_id, restaurant_rating DESC);
