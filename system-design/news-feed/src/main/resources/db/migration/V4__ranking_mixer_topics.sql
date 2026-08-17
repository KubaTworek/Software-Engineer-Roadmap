ALTER TABLE posts ADD COLUMN topics VARCHAR(500) NOT NULL DEFAULT '';

CREATE INDEX idx_posts_topics ON posts(topics);
CREATE INDEX idx_post_stats_engagement ON post_stats((like_count + comment_count) DESC, updated_at DESC);
