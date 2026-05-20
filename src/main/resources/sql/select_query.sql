-- =============================================
-- Функции, процедуры и триггеры
-- =============================================

-- 1. Функция: Расчет возраста или стажа
CREATE OR REPLACE FUNCTION get_years_from_date(p_date DATE)
    RETURNS INT
AS $$
BEGIN
    RETURN EXTRACT(YEAR FROM AGE(CURRENT_DATE, p_date))::INT;
END;
$$ LANGUAGE plpgsql;


-- 2. Функция: Количество свободных мест
CREATE OR REPLACE FUNCTION get_free_seats(p_show_id INT)
    RETURNS INT AS $$
DECLARE
    result INT;
BEGIN
    SELECT COUNT(se.seat_id)
    INTO result
    FROM seats se
             JOIN shows sh ON sh.hall_id = se.hall_id
             LEFT JOIN tickets t
                       ON t.seat_id = se.seat_id
                           AND t.show_id = p_show_id
    WHERE sh.show_id = p_show_id
      AND t.ticket_id IS NULL;

    RETURN result;
END;
$$ LANGUAGE plpgsql;


-- 3. Функция: Век по году рождения автора
CREATE OR REPLACE FUNCTION get_century(p_year INT)
    RETURNS INT AS $$
BEGIN
    IF p_year IS NULL THEN
        RETURN NULL;
    END IF;
    RETURN ((p_year - 1) / 100) + 1;
END;
$$ LANGUAGE plpgsql;


-- 4. Процедура: Назначить показ спектакля
CREATE OR REPLACE PROCEDURE add_show(
    p_spectacle_id INT,
    p_season_id INT,
    p_hall_id INT,
    p_show_date DATE,
    p_show_time TIME,
    p_is_premiere BOOLEAN
)
    LANGUAGE plpgsql
AS $$
DECLARE
    season_start DATE;
    season_end DATE;
BEGIN
    SELECT start_date, end_date
    INTO season_start, season_end
    FROM seasons
    WHERE season_id = p_season_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Сезон не найден';
    END IF;

    IF p_show_date < season_start OR p_show_date > season_end THEN
        RAISE EXCEPTION 'Дата не входит в границы сезона';
    END IF;


    IF EXISTS (
        SELECT 1 FROM shows
        WHERE hall_id = p_hall_id
          AND show_date = p_show_date
          AND show_time = p_show_time
    ) THEN
        RAISE EXCEPTION 'Зал уже занят в это время';
    END IF;

    INSERT INTO shows(
        spectacle_id,
        season_id,
        hall_id,
        show_date,
        show_time,
        is_premiere
    )
    VALUES (
               p_spectacle_id,
               p_season_id,
               p_hall_id,
               p_show_date,
               p_show_time,
               p_is_premiere
           );

    RAISE NOTICE 'Показ успешно добавлен';

END;
$$;


-- 5. Процедура: Удобное добавление актёра
CREATE OR REPLACE PROCEDURE add_actor(
    p_last_name VARCHAR,
    p_first_name VARCHAR,
    p_birth_date DATE,
    p_gender VARCHAR,
    p_salary NUMERIC,
    p_voice_type VARCHAR,
    p_height NUMERIC
)
    LANGUAGE plpgsql
AS $$
DECLARE
    new_id INT;
BEGIN
    INSERT INTO workers(
        last_name,
        first_name,
        birth_date,
        gender,
        hire_date,
        salary,
        worker_type_id
    )
    VALUES (
               p_last_name,
               p_first_name,
               p_birth_date,
               p_gender,
               CURRENT_DATE,
               p_salary,
               (SELECT type_id FROM worker_types
                WHERE type_name = 'актёр')
           )
    RETURNING worker_id INTO new_id;

    INSERT INTO actors(
        worker_id,
        voice_type,
        height
    )
    VALUES (
               new_id,
               p_voice_type,
               p_height
           );

    RAISE NOTICE 'Актёр нанят с ID %', new_id;
END;
$$;


-- 6. Процедура: Удобное добавление музыканта
CREATE OR REPLACE PROCEDURE add_musician(
    p_last_name VARCHAR,
    p_first_name VARCHAR,
    p_birth_date DATE,
    p_gender VARCHAR,
    p_salary NUMERIC,
    p_instrument VARCHAR
)
    LANGUAGE plpgsql
AS $$
DECLARE
    new_id INT;
BEGIN
    INSERT INTO workers(
        last_name, first_name, birth_date, gender,
        hire_date, salary, worker_type_id
    )
    VALUES (
        p_last_name, p_first_name, p_birth_date, p_gender,
        CURRENT_DATE, p_salary,
        (SELECT type_id FROM worker_types WHERE type_name = 'музыкант')
    )
    RETURNING worker_id INTO new_id;

    INSERT INTO musicians(worker_id, instrument)
    VALUES (new_id, p_instrument);

    RAISE NOTICE 'Музыкант нанят с ID %', new_id;
END;
$$;


-- 7. Процедура: Удобное добавление режиссёра-постановщика
CREATE OR REPLACE PROCEDURE add_director(
    p_last_name VARCHAR,
    p_first_name VARCHAR,
    p_birth_date DATE,
    p_gender VARCHAR,
    p_salary NUMERIC,
    p_specialization VARCHAR
)
    LANGUAGE plpgsql
AS $$
DECLARE
    new_id INT;
BEGIN
    INSERT INTO workers(
        last_name, first_name, birth_date, gender,
        hire_date, salary, worker_type_id
    )
    VALUES (
        p_last_name, p_first_name, p_birth_date, p_gender,
        CURRENT_DATE, p_salary,
        (SELECT type_id FROM worker_types WHERE type_name = 'режиссёр-постановщик')
    )
    RETURNING worker_id INTO new_id;

    INSERT INTO director_producers(worker_id, specialization)
    VALUES (new_id, p_specialization);

    RAISE NOTICE 'Режиссёр-постановщик нанят с ID %', new_id;
END;
$$;


-- 8. Процедура: Удобное добавление художника-постановщика
CREATE OR REPLACE PROCEDURE add_designer(
    p_last_name VARCHAR,
    p_first_name VARCHAR,
    p_birth_date DATE,
    p_gender VARCHAR,
    p_salary NUMERIC,
    p_art_style VARCHAR
)
    LANGUAGE plpgsql
AS $$
DECLARE
    new_id INT;
BEGIN
    INSERT INTO workers(
        last_name, first_name, birth_date, gender,
        hire_date, salary, worker_type_id
    )
    VALUES (
        p_last_name, p_first_name, p_birth_date, p_gender,
        CURRENT_DATE, p_salary,
        (SELECT type_id FROM worker_types WHERE type_name = 'художник-постановщик')
    )
    RETURNING worker_id INTO new_id;

    INSERT INTO designer_producers(worker_id, art_style)
    VALUES (new_id, p_art_style);

    RAISE NOTICE 'Художник-постановщик нанят с ID %', new_id;
END;
$$;


-- 9. Процедура: Удобное добавление дирижёра-постановщика
CREATE OR REPLACE PROCEDURE add_conductor(
    p_last_name VARCHAR,
    p_first_name VARCHAR,
    p_birth_date DATE,
    p_gender VARCHAR,
    p_salary NUMERIC,
    p_orchestra_type VARCHAR
)
    LANGUAGE plpgsql
AS $$
DECLARE
    new_id INT;
BEGIN
    INSERT INTO workers(
        last_name, first_name, birth_date, gender,
        hire_date, salary, worker_type_id
    )
    VALUES (
        p_last_name, p_first_name, p_birth_date, p_gender,
        CURRENT_DATE, p_salary,
        (SELECT type_id FROM worker_types WHERE type_name = 'дирижёр-постановщик')
    )
    RETURNING worker_id INTO new_id;

    INSERT INTO conductor_producers(worker_id, orchestra_type)
    VALUES (new_id, p_orchestra_type);

    RAISE NOTICE 'Дирижёр-постановщик нанят с ID %', new_id;
END;
$$;


-- 10. Процедура: Удобное добавление служащего (стафф)
CREATE OR REPLACE PROCEDURE add_staff(
    p_last_name VARCHAR,
    p_first_name VARCHAR,
    p_birth_date DATE,
    p_gender VARCHAR,
    p_salary NUMERIC,
    p_position VARCHAR
)
    LANGUAGE plpgsql
AS $$
DECLARE
    new_id INT;
BEGIN
    INSERT INTO workers(
        last_name, first_name, birth_date, gender,
        hire_date, salary, worker_type_id
    )
    VALUES (
        p_last_name, p_first_name, p_birth_date, p_gender,
        CURRENT_DATE, p_salary,
        (SELECT type_id FROM worker_types WHERE type_name = 'служащий')
    )
    RETURNING worker_id INTO new_id;

    INSERT INTO staff(worker_id, position)
    VALUES (new_id, p_position);

    RAISE NOTICE 'Служащий нанят с ID %', new_id;
END;
$$;


-- 11. Триггер: Запрет более одной роли
CREATE OR REPLACE FUNCTION check_actor_single_role()
    RETURNS TRIGGER AS $$
DECLARE
    new_spectacle_id INT;
BEGIN
    SELECT spectacle_id
    INTO new_spectacle_id
    FROM roles
    WHERE role_id = NEW.role_id;

    IF EXISTS (
        SELECT 1
        FROM casting c
                 JOIN roles r ON r.role_id = c.role_id
        WHERE c.actor_id = NEW.actor_id
          AND r.spectacle_id = new_spectacle_id
    ) THEN
        RAISE EXCEPTION
            'Актер уже имеет роль в данном спектакле';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_single_role
    BEFORE INSERT ON casting
    FOR EACH ROW
EXECUTE FUNCTION check_actor_single_role();


-- 12. Триггер: Проверка соответствия требованиям роли
CREATE OR REPLACE FUNCTION check_actor_role_requirements()
    RETURNS TRIGGER AS $$
DECLARE
    r roles%ROWTYPE;
    actor_gender VARCHAR;
    actor_voice VARCHAR;
    actor_height NUMERIC;
    actor_age INT;
BEGIN
    SELECT * INTO r
    FROM roles
    WHERE role_id = NEW.role_id;

    SELECT w.gender,
           a.voice_type,
           a.height,
           get_years_from_date(w.birth_date)
    INTO actor_gender, actor_voice, actor_height, actor_age
    FROM workers w
             JOIN actors a ON a.worker_id = w.worker_id
    WHERE w.worker_id = NEW.actor_id;

    IF r.required_gender IS NOT NULL
        AND actor_gender <> r.required_gender THEN
        RAISE EXCEPTION 'Несоответствие по полу';
    END IF;

    IF r.required_min_age IS NOT NULL
        AND actor_age < r.required_min_age THEN
        RAISE EXCEPTION 'Несоответствие по возрасту (младше нужного)';
    END IF;

    IF r.required_max_age IS NOT NULL
        AND actor_age > r.required_max_age THEN
        RAISE EXCEPTION 'Несоответствие по возрасту (взрослее нужного)';
    END IF;

    IF r.required_voice_type IS NOT NULL
        AND actor_voice <> r.required_voice_type THEN
        RAISE EXCEPTION 'Несоответствие по голосу';
    END IF;

    IF r.required_min_height IS NOT NULL
        AND actor_height < r.required_min_height THEN
        RAISE EXCEPTION 'Несоответствие по росту (меньше нужного)';
    END IF;

    IF r.required_max_height IS NOT NULL
        AND actor_height > r.required_max_height THEN
        RAISE EXCEPTION 'Несоответствие по росту (выше нужного)';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_role_requirements
    BEFORE INSERT OR UPDATE ON casting
    FOR EACH ROW
EXECUTE FUNCTION check_actor_role_requirements();


-- 13. Триггер: Запрет пересечения гастролей одного работника
CREATE OR REPLACE FUNCTION check_tour_dates_overlap()
    RETURNS TRIGGER AS $$
DECLARE
    v_new_start DATE;
    v_new_end   DATE;
    v_conflict_tour_id INT;
    v_conflict_start DATE;
    v_conflict_end   DATE;
BEGIN
    SELECT start_date, end_date
    INTO v_new_start, v_new_end
    FROM tours
    WHERE tour_id = NEW.tour_id;

    SELECT t.tour_id, t.start_date, t.end_date
    INTO v_conflict_tour_id, v_conflict_start, v_conflict_end
    FROM tour_participants tp
             JOIN tours t ON t.tour_id = tp.tour_id
    WHERE tp.worker_id = NEW.worker_id
      AND tp.tour_id   <> NEW.tour_id
      AND t.start_date <= v_new_end
      AND t.end_date   >= v_new_start
    LIMIT 1;

    IF v_conflict_tour_id IS NOT NULL THEN
        RAISE EXCEPTION
            'Работник уже участвует в гастролях (ID %) c % по %, которые пересекаются с новыми (% — %)',
            v_conflict_tour_id, v_conflict_start, v_conflict_end,
            v_new_start, v_new_end;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_tour_dates_overlap ON tour_participants;
CREATE TRIGGER trg_tour_dates_overlap
    BEFORE INSERT OR UPDATE ON tour_participants
    FOR EACH ROW
EXECUTE FUNCTION check_tour_dates_overlap();




-- =============================================
-- ЗАПРОС 1: Работники театра
-- =============================================

-- Список всех работников
-- Query #1
SELECT w.last_name,
       w.first_name,
       w.birth_date,
       w.gender,
       w.hire_date,
       w.salary,
       w.children_count,
       wt.type_name,
       get_years_from_date(w.hire_date) AS experience_years,
       get_years_from_date(w.birth_date) AS age
FROM workers w
         JOIN worker_types wt ON wt.type_id = w.worker_type_id
ORDER BY w.last_name;

-- Query #2
SELECT COUNT(*) AS total_workers
FROM workers;

-- Список всех актёров
-- Query #3
SELECT w.last_name,
       w.first_name,
       w.birth_date,
       w.gender,
       w.hire_date,
       w.salary,
       w.children_count,
       a.voice_type,
       a.height,
       a.weight,
       a.hair_color,
       a.eye_color,
       a.is_student,
       ts.school_name,
       get_years_from_date(w.hire_date) AS experience_years,
       get_years_from_date(w.birth_date) AS age
FROM workers w
         JOIN actors a ON a.worker_id = w.worker_id
         LEFT JOIN theater_schools ts ON ts.school_id = a.school_id
ORDER BY w.last_name;

-- Query #4
SELECT COUNT(*) AS total_actors
FROM workers w
         JOIN actors a ON a.worker_id = w.worker_id;

-- Список всех музыкантов
-- Query #5
SELECT w.last_name,
       w.first_name,
       w.birth_date,
       w.gender,
       w.hire_date,
       w.salary,
       w.children_count,
       m.instrument,
       get_years_from_date(w.hire_date) AS experience_years,
       get_years_from_date(w.birth_date) AS age
FROM workers w
         JOIN musicians m ON m.worker_id = w.worker_id
ORDER BY w.last_name;

-- Query #6
SELECT COUNT(*) AS total_musicians
FROM workers w
         JOIN musicians m ON m.worker_id = w.worker_id;

-- Гибкий фильтр работников
-- Query #7
SELECT w.last_name,
       w.first_name,
       w.birth_date,
       w.gender,
       w.hire_date,
       w.salary,
       w.children_count,
       wt.type_name,
       get_years_from_date(w.hire_date) AS experience_years,
       get_years_from_date(w.birth_date) AS age
FROM workers w
         JOIN worker_types wt ON wt.type_id = w.worker_type_id
WHERE (? IS NULL OR wt.type_name = ?)
  AND (? IS NULL OR get_years_from_date(w.hire_date) >= ?)
  AND (? IS NULL OR w.gender = ?)
  AND (? IS NULL OR EXTRACT(YEAR FROM w.birth_date) = ?)
  AND (? IS NULL OR get_years_from_date(w.birth_date) >= ?)
  AND (? IS NULL OR get_years_from_date(w.birth_date) <= ?)
  AND (? IS NULL OR w.children_count >= ?)
  AND (? IS NULL OR w.children_count = ?)
  AND (? IS NULL OR w.salary >= ?)
ORDER BY w.last_name;


-- =============================================
-- ЗАПРОС 2: Спектакли в репертуаре
-- =============================================

-- Гибкий список спектаклей в репертуаре
-- Query #14
SELECT DISTINCT s.title,
                g.genre_name,
                se.season_name,
                t.theater_name,
                sh.show_date,
                sh.show_time,
                sh.is_premiere
FROM spectacles s
         JOIN shows sh ON sh.spectacle_id = s.spectacle_id
         JOIN seasons se ON se.season_id = sh.season_id
         JOIN genres g ON g.genre_id = s.genre_id
         LEFT JOIN theaters t ON t.theater_id = s.theater_id
WHERE (? IS NULL OR se.season_name = ?)
  AND (? IS NULL OR g.genre_name = ?)
  AND (? IS NULL OR s.theater_id = ?)
  AND (? IS NULL OR sh.show_date >= ?)
  AND (? IS NULL OR sh.show_date <= ?)
ORDER BY sh.show_date;

-- Гибкое количество спектаклей в репертуаре
-- Query #15
SELECT COUNT(DISTINCT s.spectacle_id) AS total_in_repertoire
FROM spectacles s
         JOIN shows sh ON sh.spectacle_id = s.spectacle_id
         JOIN seasons se ON se.season_id = sh.season_id
         JOIN genres g ON g.genre_id = s.genre_id
         LEFT JOIN theaters t ON t.theater_id = s.theater_id
WHERE (? IS NULL OR se.season_name = ?)
  AND (? IS NULL OR g.genre_name = ?)
  AND (? IS NULL OR s.theater_id = ?)
  AND (? IS NULL OR sh.show_date >= ?)
  AND (? IS NULL OR sh.show_date <= ?);


-- =============================================
-- ЗАПРОС 3: Поставленные спектакли
-- =============================================

-- Все поставленные спектакли
-- Query #24
SELECT s.title,
       g.genre_name,
       s.premiere_date,
       s.creation_year,
       t.theater_name,
       ac.category_name
FROM spectacles s
         JOIN genres g ON g.genre_id = s.genre_id
         LEFT JOIN theaters t ON t.theater_id = s.theater_id
         LEFT JOIN age_categories ac ON ac.category_id = s.age_category_id
ORDER BY s.premiere_date;

-- Общее число поставленных
-- Query #25
SELECT COUNT(*) AS total_spectacles
FROM spectacles;

-- Гибкий фильтр поставленных спектаклей
-- Query #18
SELECT s.title,
       g.genre_name,
       ac.category_name,
       t.theater_name,
       s.creation_year,
       s.premiere_date
FROM spectacles s
         JOIN genres g ON g.genre_id = s.genre_id
         LEFT JOIN age_categories ac ON ac.category_id = s.age_category_id
         LEFT JOIN theaters t ON t.theater_id = s.theater_id
WHERE (? IS NULL OR g.genre_name = ?)
  AND (? IS NULL OR t.theater_id = ?)
  AND (? IS NULL OR ac.category_name = ?)
  AND (? IS NULL OR s.creation_year >= ?)
  AND (? IS NULL OR s.creation_year <= ?)
  AND (? IS NULL OR s.premiere_date >= ?)
  AND (? IS NULL OR s.premiere_date <= ?)
ORDER BY s.title;


-- =============================================
-- ЗАПРОС 4: Авторы
-- =============================================

-- Авторы поставленных спектаклей
-- Query #32
SELECT DISTINCT a.last_name,
                a.first_name,
                a.birth_year,
                a.death_year,
                c.country_name
FROM authors a
         JOIN spectacle_authors sa ON sa.author_id = a.author_id
         JOIN spectacles s ON s.spectacle_id = sa.spectacle_id
         JOIN countries c ON c.country_id = a.country_id
ORDER BY a.last_name;

-- Гибкий фильтр авторов
-- Query #33
SELECT DISTINCT a.last_name,
                a.first_name,
                a.birth_year,
                a.death_year,
                c.country_name,
                get_century(a.birth_year) AS century
FROM authors a
         JOIN spectacle_authors sa ON sa.author_id = a.author_id
         JOIN spectacles s ON s.spectacle_id = sa.spectacle_id
         JOIN countries c ON c.country_id = a.country_id
         JOIN genres g ON g.genre_id = s.genre_id
WHERE (? IS NULL OR c.country_name = ?)
  AND (? IS NULL OR get_century(a.birth_year) = ?)
  AND (? IS NULL OR g.genre_name = ?)
  AND (? IS NULL OR s.theater_id = ?)
  AND (? IS NULL OR s.premiere_date >= ?)
  AND (? IS NULL OR s.premiere_date <= ?)
ORDER BY a.last_name;


-- =============================================
-- ЗАПРОС 5: Поиск спектаклей по автору/стране/веку
-- =============================================

-- Гибкий поиск спектаклей по дополнительным критериям
-- Query #34
SELECT DISTINCT s.title,
                g.genre_name,
                ac.category_name,
                t.theater_name,
                s.creation_year,
                s.premiere_date,
                a.last_name || ' ' || a.first_name AS author_name,
                c.country_name AS author_country,
                get_century(a.birth_year) AS author_century
FROM spectacles s
         JOIN genres g ON g.genre_id = s.genre_id
         LEFT JOIN age_categories ac ON ac.category_id = s.age_category_id
         LEFT JOIN theaters t ON t.theater_id = s.theater_id
         JOIN spectacle_authors sa ON sa.spectacle_id = s.spectacle_id
         JOIN authors a ON a.author_id = sa.author_id
         JOIN countries c ON c.country_id = a.country_id
WHERE (? IS NULL OR g.genre_name = ?)
  AND (? IS NULL OR a.author_id = ?)
  AND (? IS NULL OR c.country_name = ?)
  AND (? IS NULL OR get_century(a.birth_year) = ?)
  AND (? IS NULL OR s.theater_id = ?)
  AND (? IS NULL OR s.premiere_date >= ?)
  AND (? IS NULL OR s.premiere_date <= ?)
ORDER BY s.title;


-- =============================================
-- ЗАПРОС 6: Актёры, подходящие на указанную роль
-- =============================================

-- Список актёров, подходящих на роль
-- Query #42
SELECT w.last_name,
       w.first_name,
       w.gender,
       a.voice_type,
       a.height,
       get_years_from_date(w.birth_date) AS age
FROM actors a
         JOIN workers w ON w.worker_id = a.worker_id
         JOIN roles r ON r.role_id = ?
WHERE (r.required_gender IS NULL
    OR w.gender = r.required_gender)
  AND (r.required_min_age IS NULL
    OR get_years_from_date(w.birth_date) >= r.required_min_age)
  AND (r.required_max_age IS NULL
    OR get_years_from_date(w.birth_date) <= r.required_max_age)
  AND (r.required_voice_type IS NULL
    OR a.voice_type = r.required_voice_type)
  AND (r.required_min_height IS NULL
    OR a.height >= r.required_min_height)
  AND (r.required_max_height IS NULL
    OR a.height <= r.required_max_height)
ORDER BY w.last_name;


-- =============================================
-- ЗАПРОС 7: Актёры со званиями и наградами
-- =============================================

-- Актёры имеющие звания
-- Query #43
SELECT DISTINCT w.last_name,
                w.first_name,
                ah.honor_type,
                ah.honor_name,
                ah.award_date
FROM workers w
         JOIN actors a ON a.worker_id = w.worker_id
         JOIN actor_honors ah ON ah.worker_id = a.worker_id
ORDER BY w.last_name;

-- Общее число актёров со званиями
-- Query #44
SELECT COUNT(DISTINCT w.worker_id) AS total_with_honors
FROM workers w
         JOIN actors a ON a.worker_id = w.worker_id
         JOIN actor_honors ah ON ah.worker_id = a.worker_id;

-- Гибкий фильтр актёров со званиями и наградами
-- Query #45
WITH actor_recognitions AS (
    SELECT a.worker_id,
           ah.honor_type AS recognition_type,
           ah.honor_name AS recognition_name,
           ah.award_date AS recognition_date,
           NULL::VARCHAR AS competition_name
    FROM actor_honors ah
             JOIN actors a ON a.worker_id = ah.worker_id
    UNION ALL
    SELECT a.worker_id,
           'награда' AS recognition_type,
           aw.award_name AS recognition_name,
           aw.award_date AS recognition_date,
           aw.competition_name
    FROM actor_awards aw
             JOIN actors a ON a.worker_id = aw.worker_id
)
SELECT DISTINCT w.last_name,
                w.first_name,
                w.gender,
                get_years_from_date(w.birth_date) AS age,
                ar.recognition_type,
                ar.recognition_name,
                ar.competition_name,
                ar.recognition_date
FROM actor_recognitions ar
         JOIN workers w ON w.worker_id = ar.worker_id
WHERE (? IS NULL OR ar.recognition_date >= ?)
  AND (? IS NULL OR ar.recognition_date <= ?)
  AND (? IS NULL OR ar.competition_name = ?)
  AND (? IS NULL OR w.gender = ?)
  AND (? IS NULL OR get_years_from_date(w.birth_date) >= ?)
  AND (? IS NULL OR get_years_from_date(w.birth_date) <= ?)
ORDER BY w.last_name, ar.recognition_date DESC;


-- =============================================
-- ЗАПРОС 8: Гастроли
-- =============================================

-- Гибкий фильтр гастролей (актёры и постановщики)
-- Query #50
SELECT w.last_name,
       w.first_name,
       wt.type_name,
       sp.title AS spectacle_title,
       t.start_date,
       t.end_date,
       th_from.theater_name AS from_theater,
       th_to.theater_name AS to_theater
FROM tour_participants tp
         JOIN tours t ON t.tour_id = tp.tour_id
         JOIN workers w ON w.worker_id = tp.worker_id
         JOIN worker_types wt ON wt.type_id = w.worker_type_id
         JOIN theaters th_from ON th_from.theater_id = t.from_theater_id
         JOIN theaters th_to ON th_to.theater_id = t.to_theater_id
         LEFT JOIN spectacles sp ON sp.spectacle_id = t.spectacle_id
WHERE wt.type_name IN ('актёр', 'режиссёр-постановщик',
                       'художник-постановщик', 'дирижёр-постановщик')
  AND (? IS NULL OR t.to_theater_id = ?)
  AND (? IS NULL OR t.from_theater_id = ?)
  AND (? IS NULL OR t.start_date >= ?)
  AND (? IS NULL OR t.end_date <= ?)
  AND (? IS NULL OR t.spectacle_id = ?)
ORDER BY t.start_date;


-- =============================================
-- ЗАПРОС 9: Полная информация о спектакле
-- =============================================

-- Актёры и их дублёры
-- Query #52
SELECT r.role_name,
       r.is_main_role,
       w.last_name || ' ' || w.first_name                          AS actor_name,
       CASE WHEN c.is_understudy THEN 'дублёр' ELSE 'основной' END AS role_type
FROM casting c
         JOIN roles r ON r.role_id = c.role_id
         JOIN workers w ON w.worker_id = c.actor_id
WHERE r.spectacle_id = ?
ORDER BY r.role_name, c.is_understudy;

-- Режиссёр-постановщик, художник-постановщик, дирижёр-постановщик, авторы, дата премьеры
-- Query #53
SELECT s.title,
       s.premiere_date,
       wd.last_name || ' ' || wd.first_name AS director_name,
       wa.last_name || ' ' || wa.first_name AS designer_name,
       wc.last_name || ' ' || wc.first_name AS conductor_name
FROM spectacles s
         LEFT JOIN workers wd ON wd.worker_id = s.director_id
         LEFT JOIN workers wa ON wa.worker_id = s.artist_id
         LEFT JOIN workers wc ON wc.worker_id = s.conductor_id
WHERE s.spectacle_id = ?;

-- Авторы спектакля
-- Query #54
SELECT a.last_name, a.first_name, c.country_name
FROM spectacle_authors sa
         JOIN authors a ON a.author_id = sa.author_id
         JOIN countries c ON c.country_id = a.country_id
WHERE sa.spectacle_id = ?
ORDER BY a.last_name;


-- =============================================
-- ЗАПРОС 10: Роли актёра
-- =============================================

-- Гибкий список ролей выбранного актёра (группировка/конкатенация по спектаклям)
-- Query #55
SELECT s.title AS spectacle_title,
       g.genre_name,
       ac.category_name,
       s.premiere_date,
       wd.last_name || ' ' || wd.first_name AS director_name,
       string_agg(
               r.role_name || CASE WHEN c.is_understudy THEN ' (дублёр)' ELSE '' END,
               ', ' ORDER BY r.role_name
       ) AS roles,
       COUNT(r.role_id) AS roles_count
FROM casting c
         JOIN roles r ON r.role_id = c.role_id
         JOIN spectacles s ON s.spectacle_id = r.spectacle_id
         JOIN genres g ON g.genre_id = s.genre_id
         LEFT JOIN age_categories ac ON ac.category_id = s.age_category_id
         LEFT JOIN workers wd ON wd.worker_id = s.director_id
WHERE c.actor_id = ?
  AND (? IS NULL OR s.premiere_date >= ?)
  AND (? IS NULL OR s.premiere_date <= ?)
  AND (? IS NULL OR g.genre_name = ?)
  AND (? IS NULL OR ac.category_name = ?)
  AND (? IS NULL OR s.director_id = ?)
GROUP BY s.spectacle_id, s.title, g.genre_name, ac.category_name, s.premiere_date,
         wd.last_name, wd.first_name
ORDER BY s.title;


-- =============================================
-- ЗАПРОС 11: Проданные билеты
-- =============================================

-- Число проданных билетов на все спектакли
-- Query #65
SELECT s.title, COUNT(t.ticket_id) AS tickets_sold
FROM tickets t
         JOIN shows sh ON sh.show_id = t.show_id
         JOIN spectacles s ON s.spectacle_id = sh.spectacle_id
GROUP BY s.spectacle_id, s.title
ORDER BY tickets_sold DESC;

-- Общее число проданных билетов
-- Query #66
SELECT COUNT(*) AS total_tickets_sold
FROM tickets;

-- Гибкий фильтр продаж билетов
-- Query #67
SELECT s.title,
       sh.show_date,
       sh.show_time,
       sh.is_premiere,
       COUNT(t.ticket_id) AS tickets_sold,
       SUM(CASE WHEN t.is_advance_sale THEN 1 ELSE 0 END) AS advance_sold
FROM tickets t
         JOIN shows sh ON sh.show_id = t.show_id
         JOIN spectacles s ON s.spectacle_id = sh.spectacle_id
WHERE (? IS NULL OR s.spectacle_id = ?)
  AND (? IS NULL OR sh.show_date >= ?)
  AND (? IS NULL OR sh.show_date <= ?)
  AND (? IS NULL OR sh.is_premiere = ?)
  AND (? IS NULL OR t.is_advance_sale = ?)
GROUP BY s.title, sh.show_date, sh.show_time, sh.is_premiere
ORDER BY sh.show_date;


-- =============================================
-- ЗАПРОС 12: Выручка
-- =============================================

-- Гибкая выручка (по спектаклю и/или периоду)
-- Query #75
SELECT s.title,
       SUM(t.price)       AS revenue,
       COUNT(t.ticket_id) AS tickets_sold
FROM tickets t
         JOIN shows sh ON sh.show_id = t.show_id
         JOIN spectacles s ON s.spectacle_id = sh.spectacle_id
WHERE (? IS NULL OR s.spectacle_id = ?)
  AND (? IS NULL OR sh.show_date >= ?)
  AND (? IS NULL OR sh.show_date <= ?)
GROUP BY s.spectacle_id, s.title
ORDER BY revenue DESC;


-- =============================================
-- ЗАПРОС 13: Свободные места
-- =============================================

-- Свободные места на все предстоящие спектакли
-- Query #78
SELECT s.title,
       sh.show_date,
       sh.show_time,
       h.hall_name,
       sh.is_premiere,
       get_free_seats(sh.show_id) AS free_seats
FROM shows sh
         JOIN spectacles s ON s.spectacle_id = sh.spectacle_id
         JOIN halls h ON h.hall_id = sh.hall_id
WHERE sh.show_date >= CURRENT_DATE
ORDER BY sh.show_date;

-- Гибкий фильтр свободных мест
-- Query #79
SELECT s.title,
       sh.show_date,
       sh.show_time,
       h.hall_name,
       sh.is_premiere,
       get_free_seats(sh.show_id) AS free_seats
FROM shows sh
         JOIN spectacles s ON s.spectacle_id = sh.spectacle_id
         JOIN halls h ON h.hall_id = sh.hall_id
WHERE sh.show_date >= CURRENT_DATE
  AND (? IS NULL OR s.spectacle_id = ?)
  AND (? IS NULL OR sh.is_premiere = ?)
ORDER BY sh.show_date;
