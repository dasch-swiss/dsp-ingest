CREATE TABLE project
(
    id         SERIAL PRIMARY KEY,
    shortcode  VARCHAR(4)  NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL
);
