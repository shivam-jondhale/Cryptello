CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL,
    full_name VARCHAR(255),
    email VARCHAR(255),
    mobile VARCHAR(255),
    password VARCHAR(255),
    status TINYINT,
    is_verified BIT,
    is_enabled BIT,
    send_to TINYINT,
    picture VARCHAR(255),
    gender VARCHAR(255),
    dob DATE,
    address VARCHAR(255),
    city VARCHAR(100),
    postcode VARCHAR(20),
    country VARCHAR(100),
    profile_photo VARCHAR(255),
    theme VARCHAR(50),
    user_mode VARCHAR(10),
    role TINYINT,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS users_seq (
    next_val BIGINT
);
INSERT INTO users_seq VALUES ( 1 ) ON DUPLICATE KEY UPDATE next_val=next_val;
