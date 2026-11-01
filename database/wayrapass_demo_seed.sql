-- WayraPass P1 - dataset académico/demo para PostgreSQL.
-- Requisitos: schema base + migración 20261031_d3_route_capacity.sql.
-- Credencial común de los usuarios @wayrapass.test: WayraDemo123!
-- Los datos se reconocen por emails, códigos, placas y nombres con prefijo WP Demo.

BEGIN;

-- Institución principal: se reutiliza la institución demo preexistente.
INSERT INTO institutions (name, campus_name, address, district, active)
VALUES ('Universidad Wayra', 'Campus Central', 'Av. Principal 123', 'Lima', TRUE)
ON CONFLICT (name, campus_name) DO UPDATE
SET address = EXCLUDED.address,
    district = EXCLUDED.district,
    active = TRUE;

-- Hash BCrypt compatible con BCryptPasswordEncoder para WayraDemo123!.
INSERT INTO users (email, password_hash, full_name, phone, role, enabled,
                   failed_login_attempts, locked_until)
VALUES
    ('student.demo@wayrapass.test', '$2b$12$GxK5yzXY48uOwxLXn4Ytmub5P65KzRaC1zT35aUZJMW0hqMWYveQC',
     'Estudiante Demo Principal', '+51 900 100 101', 'STUDENT', TRUE, 0, NULL),
    ('student2.demo@wayrapass.test', '$2b$12$GxK5yzXY48uOwxLXn4Ytmub5P65KzRaC1zT35aUZJMW0hqMWYveQC',
     'Estudiante Demo Secundario', '+51 900 100 102', 'STUDENT', TRUE, 0, NULL),
    ('family.demo@wayrapass.test', '$2b$12$GxK5yzXY48uOwxLXn4Ytmub5P65KzRaC1zT35aUZJMW0hqMWYveQC',
     'Familiar Demo Principal', '+51 900 200 101', 'FAMILY', TRUE, 0, NULL),
    ('family2.demo@wayrapass.test', '$2b$12$GxK5yzXY48uOwxLXn4Ytmub5P65KzRaC1zT35aUZJMW0hqMWYveQC',
     'Familiar Demo Sin Vínculo', '+51 900 200 102', 'FAMILY', TRUE, 0, NULL),
    ('driver.demo@wayrapass.test', '$2b$12$GxK5yzXY48uOwxLXn4Ytmub5P65KzRaC1zT35aUZJMW0hqMWYveQC',
     'Conductor Demo Principal', '+51 900 300 101', 'DRIVER', TRUE, 0, NULL),
    ('driver2.demo@wayrapass.test', '$2b$12$GxK5yzXY48uOwxLXn4Ytmub5P65KzRaC1zT35aUZJMW0hqMWYveQC',
     'Conductor Demo Alternativo', '+51 900 300 102', 'DRIVER', TRUE, 0, NULL),
    ('coordinator.demo@wayrapass.test', '$2b$12$GxK5yzXY48uOwxLXn4Ytmub5P65KzRaC1zT35aUZJMW0hqMWYveQC',
     'Coordinador Demo', '+51 900 400 101', 'COORDINATOR', TRUE, 0, NULL)
ON CONFLICT (email) DO UPDATE
SET password_hash = EXCLUDED.password_hash,
    full_name = EXCLUDED.full_name,
    phone = EXCLUDED.phone,
    role = EXCLUDED.role,
    enabled = TRUE,
    failed_login_attempts = 0,
    locked_until = NULL,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO students (user_id, institution_id, student_code,
                      family_link_code_hash, family_link_code_expires_at)
SELECT u.id, i.id, x.student_code, x.link_hash, x.link_expires_at
FROM (VALUES
    ('student.demo@wayrapass.test', 'WP-DEMO-001',
     '7c2523c985881fb2c2b4cfbe917eb12c4c4b61e898ad4e7160cfca487ca3c4f3',
     CURRENT_TIMESTAMP + INTERVAL '1 day'),
    ('student2.demo@wayrapass.test', 'WP-DEMO-002', NULL::VARCHAR,
     NULL::TIMESTAMPTZ)
) AS x(email, student_code, link_hash, link_expires_at)
JOIN users u ON u.email = x.email
JOIN institutions i ON i.name = 'Universidad Wayra' AND i.campus_name = 'Campus Central'
ON CONFLICT (user_id) DO UPDATE
SET institution_id = EXCLUDED.institution_id,
    student_code = EXCLUDED.student_code,
    family_link_code_hash = EXCLUDED.family_link_code_hash,
    family_link_code_expires_at = EXCLUDED.family_link_code_expires_at;

INSERT INTO drivers (user_id, license_number, license_category, license_expiration,
                     credential_image_url, authorization_status, operational_status)
SELECT u.id, x.license_number, x.category, CURRENT_DATE + INTERVAL '2 years',
       x.image_url, 'AUTHORIZED', 'AVAILABLE'
FROM (VALUES
    ('driver.demo@wayrapass.test', 'WP-DEMO-LIC-001', 'A-IIIc',
     'https://example.test/wayrapass/demo/driver-001.png'),
    ('driver2.demo@wayrapass.test', 'WP-DEMO-LIC-002', 'A-IIIc',
     'https://example.test/wayrapass/demo/driver-002.png')
) AS x(email, license_number, category, image_url)
JOIN users u ON u.email = x.email
ON CONFLICT (license_number) DO UPDATE
SET license_category = EXCLUDED.license_category,
    license_expiration = EXCLUDED.license_expiration,
    credential_image_url = EXCLUDED.credential_image_url,
    authorization_status = 'AUTHORIZED',
    operational_status = 'AVAILABLE',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO vehicles (plate, brand, model, capacity, qr_token,
                      authorization_status, operational_status)
VALUES
    ('WP-D01', 'Mercedes-Benz', 'Sprinter Demo', 32, 'WP-DEMO-QR-VEHICLE-001',
     'AUTHORIZED', 'AVAILABLE'),
    ('WP-D02', 'Toyota', 'Coaster Demo', 24, 'WP-DEMO-QR-VEHICLE-002',
     'AUTHORIZED', 'AVAILABLE'),
    ('WP-D03', 'Hyundai', 'County Demo', 40, 'WP-DEMO-QR-VEHICLE-003',
     'AUTHORIZED', 'MAINTENANCE')
ON CONFLICT (plate) DO UPDATE
SET brand = EXCLUDED.brand,
    model = EXCLUDED.model,
    capacity = EXCLUDED.capacity,
    qr_token = EXCLUDED.qr_token,
    authorization_status = EXCLUDED.authorization_status,
    operational_status = EXCLUDED.operational_status,
    updated_at = CURRENT_TIMESTAMP;

-- Capacidades contractuales DEMO: media (32), pequeña (24), grande (40).
INSERT INTO routes (institution_id, code, name, origin, destination, description,
                    capacity, status)
SELECT i.id, x.code, x.name, x.origin, x.destination, x.description,
       x.capacity, x.status
FROM (VALUES
    ('R-001', 'Ruta Centro', 'Plaza San Miguel', 'Campus Central',
     'Ruta demo principal preparada para D4.', 32, 'ACTIVE'),
    ('R-002', 'Ruta Norte Demo', 'Los Olivos', 'Campus Central',
     'Ruta demo alternativa de menor capacidad.', 24, 'ACTIVE'),
    ('R-003', 'Ruta Este Demo', 'Ate', 'Campus Central',
     'Ruta demo alternativa de mayor capacidad.', 40, 'ACTIVE')
) AS x(code, name, origin, destination, description, capacity, status)
JOIN institutions i ON i.name = 'Universidad Wayra' AND i.campus_name = 'Campus Central'
ON CONFLICT (code) DO UPDATE
SET institution_id = EXCLUDED.institution_id,
    name = EXCLUDED.name,
    origin = EXCLUDED.origin,
    destination = EXCLUDED.destination,
    description = EXCLUDED.description,
    capacity = EXCLUDED.capacity,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;

-- Paraderos georreferenciados coherentes con Lima. No existe natural key en el schema,
-- por eso se identifican por su nombre demo estable.
INSERT INTO stops (name, address, district, latitude, longitude, active)
SELECT x.name, x.address, x.district, x.latitude, x.longitude, TRUE
FROM (VALUES
    ('WP Demo - Plaza San Miguel', 'Av. La Marina 2000', 'San Miguel', -12.077500, -77.090000),
    ('WP Demo - Av. Brasil', 'Av. Brasil 2400', 'Pueblo Libre', -12.074400, -77.060800),
    ('WP Demo - Campo de Marte', 'Av. de la Peruanidad', 'Jesús María', -12.067600, -77.036700),
    ('WP Demo - Campus Central', 'Av. Principal 123', 'Lima', -12.056000, -77.030000),
    ('WP Demo - Los Olivos', 'Av. Carlos Izaguirre 800', 'Los Olivos', -11.990700, -77.070000),
    ('WP Demo - Independencia', 'Av. Túpac Amaru 1000', 'Independencia', -12.006900, -77.055500),
    ('WP Demo - Ate', 'Carretera Central 1200', 'Ate', -12.043200, -76.957800),
    ('WP Demo - San Borja', 'Av. Javier Prado Este 3000', 'San Borja', -12.090000, -77.003000)
) AS x(name, address, district, latitude, longitude)
WHERE NOT EXISTS (SELECT 1 FROM stops s WHERE s.name = x.name);

UPDATE stops s
SET address = x.address,
    district = x.district,
    latitude = x.latitude,
    longitude = x.longitude,
    active = TRUE
FROM (VALUES
    ('WP Demo - Plaza San Miguel', 'Av. La Marina 2000', 'San Miguel', -12.077500, -77.090000),
    ('WP Demo - Av. Brasil', 'Av. Brasil 2400', 'Pueblo Libre', -12.074400, -77.060800),
    ('WP Demo - Campo de Marte', 'Av. de la Peruanidad', 'Jesús María', -12.067600, -77.036700),
    ('WP Demo - Campus Central', 'Av. Principal 123', 'Lima', -12.056000, -77.030000),
    ('WP Demo - Los Olivos', 'Av. Carlos Izaguirre 800', 'Los Olivos', -11.990700, -77.070000),
    ('WP Demo - Independencia', 'Av. Túpac Amaru 1000', 'Independencia', -12.006900, -77.055500),
    ('WP Demo - Ate', 'Carretera Central 1200', 'Ate', -12.043200, -76.957800),
    ('WP Demo - San Borja', 'Av. Javier Prado Este 3000', 'San Borja', -12.090000, -77.003000)
) AS x(name, address, district, latitude, longitude)
WHERE s.name = x.name;

INSERT INTO route_stops (route_id, stop_id, stop_order,
                         estimated_minutes_from_start, active)
SELECT r.id, s.id, x.stop_order, x.minutes, TRUE
FROM (VALUES
    ('R-001', 'WP Demo - Plaza San Miguel', 1, 0),
    ('R-001', 'WP Demo - Av. Brasil', 2, 12),
    ('R-001', 'WP Demo - Campo de Marte', 3, 22),
    ('R-001', 'WP Demo - Campus Central', 4, 32),
    ('R-002', 'WP Demo - Los Olivos', 1, 0),
    ('R-002', 'WP Demo - Independencia', 2, 14),
    ('R-002', 'WP Demo - Campus Central', 3, 38),
    ('R-003', 'WP Demo - Ate', 1, 0),
    ('R-003', 'WP Demo - San Borja', 2, 18),
    ('R-003', 'WP Demo - Campus Central', 3, 36)
) AS x(route_code, stop_name, stop_order, minutes)
JOIN routes r ON r.code = x.route_code
JOIN stops s ON s.name = x.stop_name
ON CONFLICT (route_id, stop_id) DO UPDATE
SET stop_order = EXCLUDED.stop_order,
    estimated_minutes_from_start = EXCLUDED.estimated_minutes_from_start,
    active = TRUE;

INSERT INTO route_schedules (route_id, day_of_week, departure_time,
                             estimated_arrival_time, active)
SELECT r.id, x.day_of_week, x.departure_time, x.arrival_time, TRUE
FROM (VALUES
    ('R-001', 'MONDAY', TIME '06:30', TIME '07:05'),
    ('R-001', 'MONDAY', TIME '13:00', TIME '13:35'),
    ('R-001', 'TUESDAY', TIME '07:00', TIME '07:35'),
    ('R-002', 'MONDAY', TIME '06:45', TIME '07:25'),
    ('R-002', 'MONDAY', TIME '13:15', TIME '13:55'),
    ('R-003', 'MONDAY', TIME '06:50', TIME '07:30')
) AS x(route_code, day_of_week, departure_time, arrival_time)
JOIN routes r ON r.code = x.route_code
ON CONFLICT (route_id, day_of_week, departure_time) DO UPDATE
SET estimated_arrival_time = EXCLUDED.estimated_arrival_time,
    active = TRUE;

INSERT INTO student_family_links (
    student_id, family_user_id, relationship, authorized, revoked_at,
    notify_boarding, notify_arrival, notify_delay,
    notify_vehicle_change, notify_incident)
SELECT s.id, f.id, 'Madre/Padre demo', TRUE, NULL, TRUE, TRUE, TRUE, TRUE, TRUE
FROM students s
JOIN users su ON su.id = s.user_id AND su.email = 'student.demo@wayrapass.test'
JOIN users f ON f.email = 'family.demo@wayrapass.test'
ON CONFLICT (student_id, family_user_id) DO UPDATE
SET relationship = EXCLUDED.relationship,
    authorized = TRUE,
    revoked_at = NULL,
    notify_boarding = TRUE,
    notify_arrival = TRUE,
    notify_delay = TRUE,
    notify_vehicle_change = TRUE,
    notify_incident = TRUE;

-- Historial cerrado del estudiante principal.
INSERT INTO student_route_enrollments (
    student_id, route_id, stop_id, status, valid_from, valid_until)
SELECT s.id, r.id, st.id, 'ENDED', CURRENT_DATE - 90, CURRENT_DATE - 31
FROM students s
JOIN users u ON u.id = s.user_id AND u.email = 'student.demo@wayrapass.test'
JOIN routes r ON r.code = 'R-002'
JOIN stops st ON st.name = 'WP Demo - Independencia'
WHERE NOT EXISTS (
    SELECT 1 FROM student_route_enrollments e
    WHERE e.student_id = s.id AND e.route_id = r.id AND e.status = 'ENDED'
);

-- Una sola inscripción vigente por estudiante; el índice parcial resuelve la reejecución.
INSERT INTO student_route_enrollments (
    student_id, route_id, stop_id, status, valid_from,
    valid_until, suspended_from, suspended_until)
SELECT s.id, r.id, st.id, 'ACTIVE', CURRENT_DATE - 30, NULL, NULL, NULL
FROM students s
JOIN users u ON u.id = s.user_id AND u.email = 'student.demo@wayrapass.test'
JOIN routes r ON r.code = 'R-001'
JOIN stops st ON st.name = 'WP Demo - Av. Brasil'
ON CONFLICT (student_id) WHERE status IN ('ACTIVE', 'SUSPENDED') DO UPDATE
SET route_id = EXCLUDED.route_id,
    stop_id = EXCLUDED.stop_id,
    status = 'ACTIVE',
    valid_from = EXCLUDED.valid_from,
    valid_until = NULL,
    suspended_from = NULL,
    suspended_until = NULL,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO student_route_enrollments (
    student_id, route_id, stop_id, status, valid_from,
    valid_until, suspended_from, suspended_until)
SELECT s.id, r.id, st.id, 'ACTIVE', CURRENT_DATE - 10, NULL, NULL, NULL
FROM students s
JOIN users u ON u.id = s.user_id AND u.email = 'student2.demo@wayrapass.test'
JOIN routes r ON r.code = 'R-002'
JOIN stops st ON st.name = 'WP Demo - Los Olivos'
ON CONFLICT (student_id) WHERE status IN ('ACTIVE', 'SUSPENDED') DO UPDATE
SET route_id = EXCLUDED.route_id,
    stop_id = EXCLUDED.stop_id,
    status = 'ACTIVE',
    valid_from = EXCLUDED.valid_from,
    valid_until = NULL,
    suspended_from = NULL,
    suspended_until = NULL,
    updated_at = CURRENT_TIMESTAMP;

-- Viaje histórico 1: tardío, con abordaje e incidencia.
INSERT INTO trips (
    route_id, route_schedule_id, driver_id, vehicle_id,
    scheduled_start_at, scheduled_end_at, actual_start_at, actual_end_at,
    estimated_arrival_at, status, was_reassigned)
SELECT r.id, rs.id, d.id, v.id,
       CURRENT_TIMESTAMP - INTERVAL '7 days 2 hours',
       CURRENT_TIMESTAMP - INTERVAL '7 days 1 hour',
       CURRENT_TIMESTAMP - INTERVAL '7 days 1 hour 55 minutes',
       CURRENT_TIMESTAMP - INTERVAL '7 days 52 minutes',
       CURRENT_TIMESTAMP - INTERVAL '7 days 52 minutes',
       'COMPLETED', FALSE
FROM routes r
JOIN route_schedules rs ON rs.route_id = r.id
    AND rs.day_of_week = 'MONDAY' AND rs.departure_time = TIME '06:30'
JOIN drivers d ON d.license_number = 'WP-DEMO-LIC-001'
JOIN vehicles v ON v.plate = 'WP-D01'
WHERE r.code = 'R-001'
  AND NOT EXISTS (
      SELECT 1 FROM trips t
      WHERE t.route_schedule_id = rs.id AND t.driver_id = d.id
        AND t.vehicle_id = v.id AND t.status = 'COMPLETED'
  );

-- Viaje histórico 2: puntual, para reportes con más de una muestra.
INSERT INTO trips (
    route_id, route_schedule_id, driver_id, vehicle_id,
    scheduled_start_at, scheduled_end_at, actual_start_at, actual_end_at,
    estimated_arrival_at, status, was_reassigned)
SELECT r.id, rs.id, d.id, v.id,
       CURRENT_TIMESTAMP - INTERVAL '3 days 2 hours',
       CURRENT_TIMESTAMP - INTERVAL '3 days 1 hour',
       CURRENT_TIMESTAMP - INTERVAL '3 days 1 hour 58 minutes',
       CURRENT_TIMESTAMP - INTERVAL '3 days 1 hour 4 minutes',
       CURRENT_TIMESTAMP - INTERVAL '3 days 1 hour 4 minutes',
       'COMPLETED', FALSE
FROM routes r
JOIN route_schedules rs ON rs.route_id = r.id
    AND rs.day_of_week = 'MONDAY' AND rs.departure_time = TIME '06:45'
JOIN drivers d ON d.license_number = 'WP-DEMO-LIC-002'
JOIN vehicles v ON v.plate = 'WP-D02'
WHERE r.code = 'R-002'
  AND NOT EXISTS (
      SELECT 1 FROM trips t
      WHERE t.route_schedule_id = rs.id AND t.driver_id = d.id
        AND t.vehicle_id = v.id AND t.status = 'COMPLETED'
  );

-- Viaje futuro: se reposiciona en cada ejecución del seed.
WITH target AS (
    SELECT r.id AS route_id, rs.id AS schedule_id, d.id AS driver_id, v.id AS vehicle_id
    FROM routes r
    JOIN route_schedules rs ON rs.route_id = r.id
        AND rs.day_of_week = 'TUESDAY' AND rs.departure_time = TIME '07:00'
    JOIN drivers d ON d.license_number = 'WP-DEMO-LIC-002'
    JOIN vehicles v ON v.plate = 'WP-D02'
    WHERE r.code = 'R-001'
), updated AS (
    UPDATE trips t
    SET scheduled_start_at = CURRENT_TIMESTAMP + INTERVAL '1 day',
        scheduled_end_at = CURRENT_TIMESTAMP + INTERVAL '1 day 40 minutes',
        actual_start_at = NULL,
        actual_end_at = NULL,
        estimated_arrival_at = NULL,
        status = 'SCHEDULED',
        updated_at = CURRENT_TIMESTAMP
    FROM target x
    WHERE t.route_schedule_id = x.schedule_id
      AND t.driver_id = x.driver_id AND t.vehicle_id = x.vehicle_id
      AND t.status <> 'COMPLETED'
    RETURNING t.id
)
INSERT INTO trips (
    route_id, route_schedule_id, driver_id, vehicle_id,
    scheduled_start_at, scheduled_end_at, status, was_reassigned)
SELECT route_id, schedule_id, driver_id, vehicle_id,
       CURRENT_TIMESTAMP + INTERVAL '1 day',
       CURRENT_TIMESTAMP + INTERVAL '1 day 40 minutes',
       'SCHEDULED', FALSE
FROM target
WHERE NOT EXISTS (SELECT 1 FROM updated);

-- Viaje operativo D4: cuatro Stops y 16 puntos con 5 pasos por segmento.
WITH target AS (
    SELECT r.id AS route_id, rs.id AS schedule_id, d.id AS driver_id, v.id AS vehicle_id
    FROM routes r
    JOIN route_schedules rs ON rs.route_id = r.id
        AND rs.day_of_week = 'MONDAY' AND rs.departure_time = TIME '13:00'
    JOIN drivers d ON d.license_number = 'WP-DEMO-LIC-001'
    JOIN vehicles v ON v.plate = 'WP-D01'
    WHERE r.code = 'R-001'
), updated AS (
    UPDATE trips t
    SET scheduled_start_at = CURRENT_TIMESTAMP - INTERVAL '15 minutes',
        scheduled_end_at = CURRENT_TIMESTAMP + INTERVAL '45 minutes',
        actual_start_at = CURRENT_TIMESTAMP - INTERVAL '10 minutes',
        actual_end_at = NULL,
        estimated_arrival_at = CURRENT_TIMESTAMP + INTERVAL '45 minutes',
        status = 'BOARDING',
        was_reassigned = FALSE,
        updated_at = CURRENT_TIMESTAMP
    FROM target x
    WHERE t.route_schedule_id = x.schedule_id
      AND t.driver_id = x.driver_id AND t.vehicle_id = x.vehicle_id
    RETURNING t.id
)
INSERT INTO trips (
    route_id, route_schedule_id, driver_id, vehicle_id,
    scheduled_start_at, scheduled_end_at, actual_start_at,
    estimated_arrival_at, status, was_reassigned)
SELECT route_id, schedule_id, driver_id, vehicle_id,
       CURRENT_TIMESTAMP - INTERVAL '15 minutes',
       CURRENT_TIMESTAMP + INTERVAL '45 minutes',
       CURRENT_TIMESTAMP - INTERVAL '10 minutes',
       CURRENT_TIMESTAMP + INTERVAL '45 minutes',
       'BOARDING', FALSE
FROM target
WHERE NOT EXISTS (SELECT 1 FROM updated);

UPDATE vehicles v
SET operational_status = 'IN_SERVICE',
    updated_at = CURRENT_TIMESTAMP
FROM trips t
JOIN route_schedules rs ON rs.id = t.route_schedule_id
JOIN routes r ON r.id = t.route_id
WHERE v.id = t.vehicle_id
  AND v.plate = 'WP-D01'
  AND r.code = 'R-001'
  AND rs.day_of_week = 'MONDAY'
  AND rs.departure_time = TIME '13:00'
  AND t.status = 'BOARDING';

-- Abordajes solo en viajes históricos; el estudiante principal queda libre para US05.
INSERT INTO boardings (trip_id, student_id, stop_id, boarded_at, status)
SELECT t.id, s.id, st.id, t.actual_start_at + INTERVAL '8 minutes', 'CONFIRMED'
FROM trips t
JOIN route_schedules rs ON rs.id = t.route_schedule_id
JOIN students s ON s.user_id = (SELECT id FROM users WHERE email = 'student.demo@wayrapass.test')
JOIN stops st ON st.name = 'WP Demo - Av. Brasil'
WHERE rs.day_of_week = 'MONDAY' AND rs.departure_time = TIME '06:30'
  AND t.status = 'COMPLETED'
ON CONFLICT (trip_id, student_id) DO UPDATE
SET stop_id = EXCLUDED.stop_id,
    boarded_at = EXCLUDED.boarded_at,
    status = 'CONFIRMED';

INSERT INTO boardings (trip_id, student_id, stop_id, boarded_at, status)
SELECT t.id, s.id, st.id, t.actual_start_at + INTERVAL '5 minutes', 'CONFIRMED'
FROM trips t
JOIN route_schedules rs ON rs.id = t.route_schedule_id
JOIN students s ON s.user_id = (SELECT id FROM users WHERE email = 'student2.demo@wayrapass.test')
JOIN stops st ON st.name = 'WP Demo - Los Olivos'
WHERE rs.day_of_week = 'MONDAY' AND rs.departure_time = TIME '06:45'
  AND t.status = 'COMPLETED'
ON CONFLICT (trip_id, student_id) DO UPDATE
SET stop_id = EXCLUDED.stop_id,
    boarded_at = EXCLUDED.boarded_at,
    status = 'CONFIRMED';

INSERT INTO incidents (
    trip_id, driver_id, type, description, delay_minutes,
    priority, status, created_at, resolved_at)
SELECT t.id, t.driver_id, 'TRAFFIC_DELAY',
       'Incidencia histórica demo: congestión vehicular.', 8,
       'NORMAL', 'RESOLVED', t.actual_start_at + INTERVAL '20 minutes',
       t.actual_start_at + INTERVAL '35 minutes'
FROM trips t
JOIN route_schedules rs ON rs.id = t.route_schedule_id
WHERE rs.day_of_week = 'MONDAY' AND rs.departure_time = TIME '06:30'
  AND t.status = 'COMPLETED'
  AND NOT EXISTS (
      SELECT 1 FROM incidents i
      WHERE i.trip_id = t.id AND i.description = 'Incidencia histórica demo: congestión vehicular.'
  );

INSERT INTO incidents (
    trip_id, driver_id, type, description, delay_minutes,
    priority, status, created_at, resolved_at)
SELECT t.id, t.driver_id, 'STOP_UNAVAILABLE',
       'Incidencia histórica demo: paradero temporalmente cerrado.', 0,
       'NORMAL', 'RESOLVED', t.actual_start_at + INTERVAL '12 minutes',
       t.actual_start_at + INTERVAL '18 minutes'
FROM trips t
JOIN route_schedules rs ON rs.id = t.route_schedule_id
WHERE rs.day_of_week = 'MONDAY' AND rs.departure_time = TIME '06:45'
  AND t.status = 'COMPLETED'
  AND NOT EXISTS (
      SELECT 1 FROM incidents i
      WHERE i.trip_id = t.id
        AND i.description = 'Incidencia histórica demo: paradero temporalmente cerrado.'
  );

-- Notificaciones históricas mínimas; las pruebas generarán las nuevas.
INSERT INTO notifications (recipient_user_id, trip_id, incident_id, type, title, message, created_at)
SELECT u.id, b.trip_id, NULL, 'BOARDING', 'Abordaje histórico demo',
       'El estudiante demo abordó correctamente el viaje histórico.', b.boarded_at
FROM users u
JOIN students s ON s.user_id = (SELECT id FROM users WHERE email = 'student.demo@wayrapass.test')
JOIN boardings b ON b.student_id = s.id
WHERE u.email = 'family.demo@wayrapass.test'
  AND NOT EXISTS (
      SELECT 1 FROM notifications n
      WHERE n.recipient_user_id = u.id
        AND n.message = 'El estudiante demo abordó correctamente el viaje histórico.'
  );

COMMIT;

-- Resumen operativo útil tras ejecutar el seed.
SELECT u.id AS user_id, u.email, u.role
FROM users u
WHERE u.email LIKE '%.demo@wayrapass.test'
ORDER BY u.role, u.email;

SELECT r.id AS route_id, r.code, r.name, r.capacity, r.status,
       COUNT(rs.id) FILTER (WHERE rs.active) AS active_stops
FROM routes r
LEFT JOIN route_stops rs ON rs.route_id = r.id
WHERE r.code IN ('R-001', 'R-002', 'R-003')
GROUP BY r.id, r.code, r.name, r.capacity, r.status
ORDER BY r.code;

SELECT t.id AS trip_id, r.code AS route_code, t.status,
       t.driver_id, t.vehicle_id, t.scheduled_start_at, t.scheduled_end_at
FROM trips t
JOIN routes r ON r.id = t.route_id
WHERE r.code IN ('R-001', 'R-002', 'R-003')
ORDER BY t.scheduled_start_at;
