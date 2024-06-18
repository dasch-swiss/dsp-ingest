CREATE TABLE project
(
    id         SERIAL PRIMARY KEY,
    shortcode  VARCHAR(4)  NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL
);