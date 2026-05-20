-- =============================================
-- СОЗДАНИЕ ТАБЛИЦ
-- =============================================

-- ----------------------------------------
-- Вспомогательные таблицы
-- ----------------------------------------

CREATE TABLE worker_types (
    type_id SERIAL PRIMARY KEY,
    type_name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE countries (
    country_id SERIAL PRIMARY KEY,
    country_name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE city (
    city_id SERIAL PRIMARY KEY,
    city_name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE theater_schools (
    school_id SERIAL PRIMARY KEY,
    school_name VARCHAR(200) NOT NULL UNIQUE,
    city_id INT NOT NULL REFERENCES city(city_id)
);

CREATE TABLE genres (
    genre_id SERIAL PRIMARY KEY,
    genre_name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE age_categories (
    category_id SERIAL PRIMARY KEY,
    category_name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE theaters (
    theater_id SERIAL PRIMARY KEY,
    theater_name VARCHAR(200) NOT NULL,
    city_id INT NOT NULL REFERENCES city(city_id),
    address VARCHAR(300)
);

CREATE TABLE current_theater (
    theater_id INT PRIMARY KEY REFERENCES theaters(theater_id)
);

CREATE TABLE seat_types (
    seat_type_id SERIAL PRIMARY KEY,
    type_name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE seasons (
    season_id SERIAL PRIMARY KEY,
    season_name VARCHAR(50) NOT NULL UNIQUE,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    CHECK (end_date >= start_date)
);

CREATE TABLE halls (
    hall_id SERIAL PRIMARY KEY,
    hall_name VARCHAR(100) NOT NULL UNIQUE
);


-- ----------------------------------------
-- Работники
-- ----------------------------------------

CREATE TABLE workers (
    worker_id SERIAL PRIMARY KEY,
    last_name VARCHAR(100) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    birth_date DATE NOT NULL,
    gender VARCHAR(10) NOT NULL CHECK (gender IN ('М', 'Ж')),
    hire_date DATE NOT NULL,
    salary DECIMAL(10,2) NOT NULL CHECK (salary > 0),
    worker_type_id INT NOT NULL REFERENCES worker_types(type_id),
    children_count INT NOT NULL DEFAULT 0 CHECK (children_count >= 0)
);


-- ----------------------------------------
-- Подтипы работников
-- ----------------------------------------

CREATE TABLE actors (
    worker_id INT PRIMARY KEY REFERENCES workers(worker_id) ON DELETE CASCADE,
    voice_type VARCHAR(50),
    height DECIMAL(5,2) CHECK (height > 0),
    weight DECIMAL(5,2) CHECK (weight > 0),
    hair_color VARCHAR(30),
    eye_color VARCHAR(30),
    is_student BOOLEAN NOT NULL DEFAULT FALSE,
    school_id INT REFERENCES theater_schools(school_id)
);

CREATE TABLE musicians (
    worker_id INT PRIMARY KEY REFERENCES workers(worker_id) ON DELETE CASCADE,
    instrument VARCHAR(100) NOT NULL
);

CREATE TABLE director_producers (
    worker_id INT PRIMARY KEY REFERENCES workers(worker_id) ON DELETE CASCADE,
    specialization VARCHAR(100)
);

CREATE TABLE designer_producers (
    worker_id INT PRIMARY KEY REFERENCES workers(worker_id) ON DELETE CASCADE,
    art_style VARCHAR(100)
);

CREATE TABLE conductor_producers (
    worker_id INT PRIMARY KEY REFERENCES workers(worker_id) ON DELETE CASCADE,
    orchestra_type VARCHAR(100)
);

CREATE TABLE staff (
    worker_id INT PRIMARY KEY REFERENCES workers(worker_id) ON DELETE CASCADE,
    position VARCHAR(100) NOT NULL
);


-- ----------------------------------------
-- Звания и награды актёров
-- ----------------------------------------

CREATE TABLE actor_honors (
    honor_id SERIAL PRIMARY KEY,
    worker_id INT NOT NULL REFERENCES actors(worker_id) ON DELETE CASCADE,
    honor_type VARCHAR(30) NOT NULL,
    honor_name VARCHAR(200) NOT NULL,
    award_date DATE NOT NULL
);

CREATE TABLE actor_awards (
    award_id SERIAL PRIMARY KEY,
    worker_id INT NOT NULL REFERENCES actors(worker_id) ON DELETE CASCADE,
    competition_name VARCHAR(200) NOT NULL,
    award_name VARCHAR(200) NOT NULL,
    award_date DATE NOT NULL
);


-- ----------------------------------------
-- Авторы
-- ----------------------------------------

CREATE TABLE authors (
    author_id SERIAL PRIMARY KEY,
    last_name VARCHAR(100) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    birth_year INT,
    death_year INT,
    country_id INT NOT NULL REFERENCES countries(country_id)
    CHECK (birth_year <= death_year)
);


-- ----------------------------------------
-- Спектакли
-- ----------------------------------------

CREATE TABLE spectacles (
    spectacle_id SERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    genre_id INT NOT NULL REFERENCES genres(genre_id),
    age_category_id INT REFERENCES age_categories(category_id),
    creation_year INT,
    director_id INT NOT NULL REFERENCES director_producers(worker_id),
    artist_id INT REFERENCES designer_producers(worker_id),
    conductor_id INT REFERENCES conductor_producers(worker_id),
    theater_id INT REFERENCES theaters(theater_id),
    premiere_date DATE
);

CREATE TABLE spectacle_authors (
    spectacle_id INT NOT NULL REFERENCES spectacles(spectacle_id) ON DELETE CASCADE,
    author_id INT NOT NULL REFERENCES authors(author_id) ON DELETE CASCADE,
    PRIMARY KEY (spectacle_id, author_id)
);


-- ----------------------------------------
-- Роли и распределение
-- ----------------------------------------

CREATE TABLE roles (
    role_id SERIAL PRIMARY KEY,
    spectacle_id INT NOT NULL REFERENCES spectacles(spectacle_id) ON DELETE CASCADE,
    role_name VARCHAR(100) NOT NULL,
    is_main_role BOOLEAN NOT NULL DEFAULT FALSE,
    required_gender VARCHAR(10),
    required_min_age INT,
    required_max_age INT,
    required_voice_type VARCHAR(50),
    required_min_height DECIMAL(5,2),
    required_max_height DECIMAL(5,2)
    CHECK (required_min_age IS NULL OR required_min_age >= 0)
    CHECK (required_max_age IS NULL OR required_max_age >= required_min_age)
    CHECK (required_min_height IS NULL OR required_min_height >= 0)
    CHECK (required_max_height IS NULL OR required_max_height >= required_min_height)
);

CREATE TABLE casting (
    casting_id SERIAL PRIMARY KEY,
    role_id INT NOT NULL REFERENCES roles(role_id) ON DELETE CASCADE,
    actor_id INT NOT NULL REFERENCES actors(worker_id) ON DELETE CASCADE,
    is_understudy BOOLEAN NOT NULL DEFAULT FALSE
);


-- ----------------------------------------
-- Зал и места
-- ----------------------------------------

CREATE TABLE seats (
    seat_id SERIAL PRIMARY KEY,
    hall_id INT NOT NULL REFERENCES halls(hall_id) ON DELETE CASCADE,
    seat_type_id INT NOT NULL REFERENCES seat_types(seat_type_id),
    row_number INT NOT NULL CHECK (row_number > 0),
    seat_number INT NOT NULL CHECK (seat_number > 0),
    UNIQUE (hall_id, seat_type_id, row_number, seat_number)
);


-- ----------------------------------------
-- Ценообразование
-- ----------------------------------------

CREATE TABLE pricing (
    pricing_id SERIAL PRIMARY KEY,
    spectacle_id INT NOT NULL REFERENCES spectacles(spectacle_id) ON DELETE CASCADE,
    seat_type_id INT NOT NULL REFERENCES seat_types(seat_type_id),
    is_premiere BOOLEAN NOT NULL DEFAULT FALSE,
    price DECIMAL(10,2) NOT NULL CHECK (price >= 0),
    UNIQUE (spectacle_id, seat_type_id, is_premiere)
);


-- ----------------------------------------
-- Показы
-- ----------------------------------------

CREATE TABLE shows (
    show_id SERIAL PRIMARY KEY,
    spectacle_id INT NOT NULL REFERENCES spectacles(spectacle_id),
    season_id INT NOT NULL REFERENCES seasons(season_id),
    hall_id INT NOT NULL REFERENCES halls(hall_id),
    show_date DATE NOT NULL,
    show_time TIME NOT NULL,
    is_premiere BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (hall_id, show_date, show_time)
);


-- ----------------------------------------
-- Билеты
-- ----------------------------------------

CREATE TABLE tickets (
    ticket_id SERIAL PRIMARY KEY,
    show_id INT NOT NULL REFERENCES shows(show_id),
    seat_id INT NOT NULL REFERENCES seats(seat_id),
    price DECIMAL(10,2) NOT NULL CHECK (price >= 0),
    sale_date DATE NOT NULL,
    is_advance_sale BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (show_id, seat_id)
);


-- ----------------------------------------
-- Гастроли
-- ----------------------------------------

CREATE TABLE tours (
    tour_id SERIAL PRIMARY KEY,
    spectacle_id INT REFERENCES spectacles(spectacle_id),
    from_theater_id INT NOT NULL REFERENCES theaters(theater_id),
    to_theater_id INT NOT NULL REFERENCES theaters(theater_id),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    CHECK (end_date >= start_date),
    CHECK (from_theater_id <> to_theater_id)
);

CREATE TABLE tour_participants (
    tour_id INT NOT NULL REFERENCES tours(tour_id) ON DELETE CASCADE,
    worker_id INT NOT NULL REFERENCES workers(worker_id) ON DELETE CASCADE,
    PRIMARY KEY (tour_id, worker_id)
);