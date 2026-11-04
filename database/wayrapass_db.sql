-- ============================================================
-- WayraPass - Base de Datos PostgreSQL
-- ============================================================

BEGIN;

-- ============================================================
-- 1. USERS
-- Cuenta común para los cuatro roles:
-- STUDENT, FAMILY, DRIVER, COORDINATOR
-- ============================================================

CREATE TABLE users (
    id                  BIGSERIAL PRIMARY KEY,
    email               VARCHAR(150) NOT NULL UNIQUE,
    password_hash       VARCHAR(255) NOT NULL,
    full_name           VARCHAR(150) NOT NULL,
    phone               VARCHAR(30),
    role                VARCHAR(20) NOT NULL,
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,

    -- Soporte para bloqueo temporal después de intentos fallidos.
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until         TIMESTAMPTZ,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_users_role
        CHECK (role IN ('STUDENT', 'FAMILY', 'DRIVER', 'COORDINATOR')),

    CONSTRAINT chk_users_failed_attempts
        CHECK (failed_login_attempts >= 0)
);


-- ============================================================
-- 2. PASSWORD_RESET_TOKENS
-- Recuperación de contraseña de US01.
-- Se almacena el hash del token, no el token plano.
-- ============================================================

CREATE TABLE password_reset_tokens (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    token_hash          VARCHAR(255) NOT NULL UNIQUE,
    expires_at          TIMESTAMPTZ NOT NULL,
    used_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_password_reset_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);


-- ============================================================
-- 3. INSTITUTIONS
-- Universidad / instituto / campus atendido por WayraPass.
-- ============================================================

CREATE TABLE institutions (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(150) NOT NULL,
    campus_name         VARCHAR(150),
    address             VARCHAR(250),
    district            VARCHAR(100),
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_institution_name_campus
        UNIQUE (name, campus_name)
);


-- ============================================================
-- 4. STUDENTS
-- Perfil específico del usuario STUDENT.
-- El código temporal de vinculación permite asociar familiares.
-- ============================================================

CREATE TABLE students (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL UNIQUE,
    institution_id      BIGINT NOT NULL,
    student_code        VARCHAR(40),

    -- Código temporal para vinculación con un familiar.
    -- Idealmente el backend guarda aquí un hash del código.
    family_link_code_hash VARCHAR(255),
    family_link_code_expires_at TIMESTAMPTZ,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_student_user
        FOREIGN KEY (user_id)
        REFERENCES users(id),

    CONSTRAINT fk_student_institution
        FOREIGN KEY (institution_id)
        REFERENCES institutions(id),

    CONSTRAINT uq_student_code_institution
        UNIQUE (institution_id, student_code)
);


-- ============================================================
-- 5. DRIVERS
-- Perfil específico del usuario DRIVER.
-- authorization_status representa la verificación/habilitación.
-- operational_status representa disponibilidad operativa.
-- ============================================================

CREATE TABLE drivers (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL UNIQUE,

    license_number      VARCHAR(50) NOT NULL UNIQUE,
    license_category    VARCHAR(20) NOT NULL,
    license_expiration  DATE,

    credential_image_url VARCHAR(500),

    authorization_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    operational_status   VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',

    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_driver_user
        FOREIGN KEY (user_id)
        REFERENCES users(id),

    CONSTRAINT chk_driver_authorization
        CHECK (authorization_status IN ('PENDING', 'AUTHORIZED', 'REJECTED')),

    CONSTRAINT chk_driver_operational_status
        CHECK (operational_status IN ('AVAILABLE', 'UNAVAILABLE', 'INACTIVE'))
);


-- ============================================================
-- 6. STUDENT_FAMILY_LINKS
-- Relación N:M entre estudiantes y familiares autorizados.
-- Las preferencias de notificación viven aquí porque pertenecen
-- a la relación "este familiar supervisa a este estudiante".
-- ============================================================

CREATE TABLE student_family_links (
    id                  BIGSERIAL PRIMARY KEY,
    student_id          BIGINT NOT NULL,
    family_user_id      BIGINT NOT NULL,

    relationship        VARCHAR(50),
    authorized          BOOLEAN NOT NULL DEFAULT TRUE,
    linked_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at          TIMESTAMPTZ,

    notify_boarding     BOOLEAN NOT NULL DEFAULT TRUE,
    notify_arrival      BOOLEAN NOT NULL DEFAULT TRUE,
    notify_delay        BOOLEAN NOT NULL DEFAULT TRUE,
    notify_vehicle_change BOOLEAN NOT NULL DEFAULT TRUE,
    notify_incident     BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_family_link_student
        FOREIGN KEY (student_id)
        REFERENCES students(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_family_link_user
        FOREIGN KEY (family_user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_student_family_link
        UNIQUE (student_id, family_user_id)
);


-- ============================================================
-- 7. ROUTES
-- Definición estable de una ruta.
-- No contiene chofer ni unidad: estos pertenecen a cada viaje.
-- ============================================================

CREATE TABLE routes (
    id                  BIGSERIAL PRIMARY KEY,
    institution_id      BIGINT NOT NULL,

    code                VARCHAR(30) NOT NULL UNIQUE,
    name                VARCHAR(150) NOT NULL,
    origin              VARCHAR(200) NOT NULL,
    destination         VARCHAR(200) NOT NULL,
    description         VARCHAR(500),
    capacity            INTEGER NOT NULL,

    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_route_institution
        FOREIGN KEY (institution_id)
        REFERENCES institutions(id),

    CONSTRAINT chk_route_status
        CHECK (status IN ('ACTIVE', 'INACTIVE')),

    CONSTRAINT chk_route_capacity
        CHECK (capacity > 0)
);


-- ============================================================
-- 8. STOPS
-- Paraderos físicos.
-- ============================================================

CREATE TABLE stops (
    id                  BIGSERIAL PRIMARY KEY,

    name                VARCHAR(150) NOT NULL,
    address             VARCHAR(250),
    district            VARCHAR(100),

    latitude            NUMERIC(9,6),
    longitude           NUMERIC(9,6),

    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_stop_latitude
        CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),

    CONSTRAINT chk_stop_longitude
        CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180)
);


-- ============================================================
-- 9. ROUTE_STOPS
-- Tabla intermedia para la relación N:M ROUTE <-> STOP.
-- stop_order define el orden del recorrido.
-- estimated_minutes_from_start ayuda con horarios/ETA.
-- ============================================================

CREATE TABLE route_stops (
    id                  BIGSERIAL PRIMARY KEY,
    route_id            BIGINT NOT NULL,
    stop_id             BIGINT NOT NULL,

    stop_order          INTEGER NOT NULL,
    estimated_minutes_from_start INTEGER NOT NULL DEFAULT 0,
    active              BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_route_stop_route
        FOREIGN KEY (route_id)
        REFERENCES routes(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_route_stop_stop
        FOREIGN KEY (stop_id)
        REFERENCES stops(id),

    CONSTRAINT uq_route_stop
        UNIQUE (route_id, stop_id),

    CONSTRAINT uq_route_stop_order
        UNIQUE (route_id, stop_order),

    CONSTRAINT chk_route_stop_order
        CHECK (stop_order > 0),

    CONSTRAINT chk_route_stop_estimated_minutes
        CHECK (estimated_minutes_from_start >= 0)
);


-- ============================================================
-- 10. ROUTE_SCHEDULES
-- Horarios recurrentes de una ruta.
-- Ej.: R-12 / MONDAY / 06:45.
-- ============================================================

CREATE TABLE route_schedules (
    id                  BIGSERIAL PRIMARY KEY,
    route_id            BIGINT NOT NULL,

    day_of_week         VARCHAR(10) NOT NULL,
    departure_time      TIME NOT NULL,
    estimated_arrival_time TIME,

    active              BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_route_schedule_route
        FOREIGN KEY (route_id)
        REFERENCES routes(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_route_schedule_day
        CHECK (
            day_of_week IN (
                'MONDAY',
                'TUESDAY',
                'WEDNESDAY',
                'THURSDAY',
                'FRIDAY',
                'SATURDAY',
                'SUNDAY'
            )
        ),

    CONSTRAINT uq_route_schedule
        UNIQUE (route_id, day_of_week, departure_time)
);


-- ============================================================
-- 11. STUDENT_ROUTE_ENROLLMENTS
-- Relación N:M histórica STUDENT <-> ROUTE.
--
-- Cada fila representa una inscripción/asignación del estudiante
-- a una ruta y a uno de los paraderos pertenecientes a esa ruta.
-- Al cambiar de ruta se cierra la fila anterior y se crea otra,
-- conservando así el historial.
-- ============================================================

CREATE TABLE student_route_enrollments (
    id                  BIGSERIAL PRIMARY KEY,
    student_id          BIGINT NOT NULL,
    route_id            BIGINT NOT NULL,
    stop_id             BIGINT NOT NULL,

    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    valid_from          DATE NOT NULL DEFAULT CURRENT_DATE,
    valid_until         DATE,

    suspended_from      DATE,
    suspended_until     DATE,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_enrollment_student
        FOREIGN KEY (student_id)
        REFERENCES students(id),

    CONSTRAINT fk_enrollment_route
        FOREIGN KEY (route_id)
        REFERENCES routes(id),

    -- Garantiza que el paradero elegido realmente pertenezca
    -- a la ruta elegida.
    CONSTRAINT fk_enrollment_route_stop
        FOREIGN KEY (route_id, stop_id)
        REFERENCES route_stops(route_id, stop_id),

    CONSTRAINT chk_enrollment_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'ENDED')),

    CONSTRAINT chk_enrollment_dates
        CHECK (valid_until IS NULL OR valid_until >= valid_from),

    CONSTRAINT chk_enrollment_suspension
        CHECK (
            suspended_until IS NULL
            OR suspended_from IS NULL
            OR suspended_until >= suspended_from
        )
);

-- Solo una asignación vigente (activa o suspendida) por estudiante.
CREATE UNIQUE INDEX uq_current_enrollment_per_student
    ON student_route_enrollments(student_id)
    WHERE status IN ('ACTIVE', 'SUSPENDED');


-- ============================================================
-- 12. FAVORITE_ROUTES
-- Relación N:M STUDENT <-> ROUTE para US02.
-- ============================================================

CREATE TABLE favorite_routes (
    student_id          BIGINT NOT NULL,
    route_id            BIGINT NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (student_id, route_id),

    CONSTRAINT fk_favorite_student
        FOREIGN KEY (student_id)
        REFERENCES students(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_favorite_route
        FOREIGN KEY (route_id)
        REFERENCES routes(id)
        ON DELETE CASCADE
);


-- ============================================================
-- 13. VEHICLES
-- Unidades disponibles para los viajes.
-- El qr_token se genera desde el backend y se codifica en el QR.
-- ============================================================

CREATE TABLE vehicles (
    id                  BIGSERIAL PRIMARY KEY,

    plate               VARCHAR(20) NOT NULL UNIQUE,
    brand               VARCHAR(60),
    model               VARCHAR(60),
    capacity            INTEGER NOT NULL,

    qr_token            VARCHAR(120) NOT NULL UNIQUE,

    authorization_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    operational_status   VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',

    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_vehicle_capacity
        CHECK (capacity > 0),

    CONSTRAINT chk_vehicle_authorization
        CHECK (authorization_status IN ('PENDING', 'AUTHORIZED', 'REJECTED')),

    CONSTRAINT chk_vehicle_operational_status
        CHECK (
            operational_status IN (
                'AVAILABLE',
                'IN_SERVICE',
                'MAINTENANCE',
                'UNAVAILABLE',
                'INACTIVE'
            )
        )
);


-- ============================================================
-- 14. TRIPS
-- Entidad central operativa.
--
-- ROUTE = definición del recorrido.
-- TRIP  = ejecución concreta de esa ruta en una fecha/hora.
--
-- driver_id y vehicle_id son NULLABLE porque un viaje puede ser
-- programado antes de que el coordinador asigne recursos.
-- ============================================================

CREATE TABLE trips (
    id                  BIGSERIAL PRIMARY KEY,

    route_id            BIGINT NOT NULL,
    route_schedule_id   BIGINT,

    driver_id           BIGINT,
    vehicle_id          BIGINT,

    scheduled_start_at  TIMESTAMPTZ NOT NULL,
    scheduled_end_at    TIMESTAMPTZ,

    actual_start_at     TIMESTAMPTZ,
    actual_end_at       TIMESTAMPTZ,

    estimated_arrival_at TIMESTAMPTZ,

    status              VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    was_reassigned      BOOLEAN NOT NULL DEFAULT FALSE,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_trip_route
        FOREIGN KEY (route_id)
        REFERENCES routes(id),

    CONSTRAINT fk_trip_route_schedule
        FOREIGN KEY (route_schedule_id)
        REFERENCES route_schedules(id),

    CONSTRAINT fk_trip_driver
        FOREIGN KEY (driver_id)
        REFERENCES drivers(id),

    CONSTRAINT fk_trip_vehicle
        FOREIGN KEY (vehicle_id)
        REFERENCES vehicles(id),

    CONSTRAINT chk_trip_status
        CHECK (
            status IN (
                'SCHEDULED',
                'BOARDING',
                'IN_PROGRESS',
                'COMPLETED',
                'CANCELLED'
            )
        ),

    CONSTRAINT chk_trip_scheduled_dates
        CHECK (
            scheduled_end_at IS NULL
            OR scheduled_end_at > scheduled_start_at
        ),

    CONSTRAINT chk_trip_actual_dates
        CHECK (
            actual_end_at IS NULL
            OR actual_start_at IS NULL
            OR actual_end_at >= actual_start_at
        )
);


-- ============================================================
-- 15. BOARDINGS
-- Registro de asistencia / abordaje por QR.
-- UNIQUE(trip_id, student_id) evita doble abordaje.
-- ============================================================

CREATE TABLE boardings (
    id                  BIGSERIAL PRIMARY KEY,

    trip_id             BIGINT NOT NULL,
    student_id          BIGINT NOT NULL,
    stop_id             BIGINT NOT NULL,

    boarded_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status              VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',

    CONSTRAINT fk_boarding_trip
        FOREIGN KEY (trip_id)
        REFERENCES trips(id),

    CONSTRAINT fk_boarding_student
        FOREIGN KEY (student_id)
        REFERENCES students(id),

    CONSTRAINT fk_boarding_stop
        FOREIGN KEY (stop_id)
        REFERENCES stops(id),

    CONSTRAINT uq_boarding_student_trip
        UNIQUE (trip_id, student_id),

    CONSTRAINT chk_boarding_status
        CHECK (status IN ('CONFIRMED', 'CANCELLED'))
);


-- ============================================================
-- 16. INCIDENTS
-- Retrasos, desvíos, fallas de unidad, etc.
-- delay_minutes permite recalcular el ETA y generar alertas.
-- ============================================================

CREATE TABLE incidents (
    id                  BIGSERIAL PRIMARY KEY,

    trip_id             BIGINT NOT NULL,
    driver_id           BIGINT NOT NULL,

    type                VARCHAR(30) NOT NULL,
    description         VARCHAR(1000),

    delay_minutes       INTEGER NOT NULL DEFAULT 0,
    priority            VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    status              VARCHAR(20) NOT NULL DEFAULT 'OPEN',

    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at         TIMESTAMPTZ,

    CONSTRAINT fk_incident_trip
        FOREIGN KEY (trip_id)
        REFERENCES trips(id),

    CONSTRAINT fk_incident_driver
        FOREIGN KEY (driver_id)
        REFERENCES drivers(id),

    CONSTRAINT chk_incident_type
        CHECK (
            type IN (
                'TRAFFIC_DELAY',
                'DETOUR',
                'VEHICLE_FAILURE',
                'STOP_UNAVAILABLE',
                'OTHER'
            )
        ),

    CONSTRAINT chk_incident_delay
        CHECK (delay_minutes >= 0),

    CONSTRAINT chk_incident_priority
        CHECK (priority IN ('NORMAL', 'HIGH', 'CRITICAL')),

    CONSTRAINT chk_incident_status
        CHECK (status IN ('OPEN', 'RESOLVED'))
);


-- ============================================================
-- 17. TRIP_LOCATIONS
-- Posiciones del recorrido.
--
-- IMPORTANTE PARA EL MVP:
-- Estas coordenadas NO provienen de GPS físico.
-- Serán generadas por un script de simulación y enviadas
-- periódicamente al backend.
-- ============================================================

CREATE TABLE trip_locations (
    id                  BIGSERIAL PRIMARY KEY,

    trip_id             BIGINT NOT NULL,

    latitude            NUMERIC(9,6) NOT NULL,
    longitude           NUMERIC(9,6) NOT NULL,

    recorded_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_trip_location_trip
        FOREIGN KEY (trip_id)
        REFERENCES trips(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_trip_location_latitude
        CHECK (latitude BETWEEN -90 AND 90),

    CONSTRAINT chk_trip_location_longitude
        CHECK (longitude BETWEEN -180 AND 180)
);


-- ============================================================
-- 18. NOTIFICATIONS
-- Notificaciones de abordaje, llegada, retraso, incidencia,
-- cambio de unidad/chofer, etc.
-- ============================================================

CREATE TABLE notifications (
    id                  BIGSERIAL PRIMARY KEY,

    recipient_user_id   BIGINT NOT NULL,
    trip_id             BIGINT,
    incident_id         BIGINT,

    type                VARCHAR(30) NOT NULL,
    title               VARCHAR(150),
    message             VARCHAR(1000) NOT NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at             TIMESTAMPTZ,

    CONSTRAINT fk_notification_user
        FOREIGN KEY (recipient_user_id)
        REFERENCES users(id),

    CONSTRAINT fk_notification_trip
        FOREIGN KEY (trip_id)
        REFERENCES trips(id),

    CONSTRAINT fk_notification_incident
        FOREIGN KEY (incident_id)
        REFERENCES incidents(id),

    CONSTRAINT chk_notification_type
        CHECK (
            type IN (
                'BOARDING',
                'ARRIVAL',
                'DELAY',
                'INCIDENT',
                'VEHICLE_CHANGE',
                'DRIVER_CHANGE',
                'GENERAL'
            )
        )
);


-- ============================================================
-- 19. AI_ACTION_REQUESTS
-- Soporte mínimo para WayrIA.
--
-- Las consultas y resúmenes de IA NO necesitan tabla propia.
-- Esta tabla registra solo acciones críticas propuestas por la IA
-- que necesitan confirmación antes de modificar un viaje.
-- ============================================================

CREATE TABLE ai_action_requests (
    id                  BIGSERIAL PRIMARY KEY,

    requested_by_user_id BIGINT NOT NULL,
    confirmed_by_user_id BIGINT,

    trip_id             BIGINT NOT NULL,

    action_type         VARCHAR(30) NOT NULL,

    proposed_driver_id  BIGINT,
    proposed_vehicle_id BIGINT,

    request_text        VARCHAR(1000) NOT NULL,
    ai_explanation      VARCHAR(1500),

    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at        TIMESTAMPTZ,
    executed_at         TIMESTAMPTZ,

    CONSTRAINT fk_ai_request_user
        FOREIGN KEY (requested_by_user_id)
        REFERENCES users(id),

    CONSTRAINT fk_ai_confirmed_user
        FOREIGN KEY (confirmed_by_user_id)
        REFERENCES users(id),

    CONSTRAINT fk_ai_trip
        FOREIGN KEY (trip_id)
        REFERENCES trips(id),

    CONSTRAINT fk_ai_proposed_driver
        FOREIGN KEY (proposed_driver_id)
        REFERENCES drivers(id),

    CONSTRAINT fk_ai_proposed_vehicle
        FOREIGN KEY (proposed_vehicle_id)
        REFERENCES vehicles(id),

    CONSTRAINT chk_ai_action_type
        CHECK (
            action_type IN (
                'REASSIGN_DRIVER',
                'REASSIGN_VEHICLE',
                'REASSIGN_BOTH'
            )
        ),

    CONSTRAINT chk_ai_action_status
        CHECK (
            status IN (
                'PENDING',
                'CONFIRMED',
                'EXECUTED',
                'REJECTED',
                'FAILED'
            )
        ),

    CONSTRAINT chk_ai_action_has_proposal
        CHECK (
            proposed_driver_id IS NOT NULL
            OR proposed_vehicle_id IS NOT NULL
        )
);


-- ============================================================
-- ÍNDICES
-- Mejoran las búsquedas más frecuentes del MVP.
-- ============================================================

CREATE INDEX idx_students_institution
    ON students(institution_id);

CREATE INDEX idx_routes_institution_status
    ON routes(institution_id, status);

CREATE INDEX idx_route_stops_route_order
    ON route_stops(route_id, stop_order);

CREATE INDEX idx_route_schedules_route
    ON route_schedules(route_id);

CREATE INDEX idx_enrollments_route_status
    ON student_route_enrollments(route_id, status);

CREATE INDEX idx_enrollments_student
    ON student_route_enrollments(student_id);

CREATE INDEX idx_trips_route_scheduled
    ON trips(route_id, scheduled_start_at);

CREATE INDEX idx_trips_driver_scheduled
    ON trips(driver_id, scheduled_start_at);

CREATE INDEX idx_trips_vehicle_scheduled
    ON trips(vehicle_id, scheduled_start_at);

CREATE INDEX idx_trips_status
    ON trips(status);

CREATE INDEX idx_boardings_trip
    ON boardings(trip_id);

CREATE INDEX idx_boardings_student
    ON boardings(student_id);

CREATE INDEX idx_incidents_trip_created
    ON incidents(trip_id, created_at);

CREATE INDEX idx_trip_locations_trip_recorded
    ON trip_locations(trip_id, recorded_at DESC);

CREATE INDEX idx_notifications_user_created
    ON notifications(recipient_user_id, created_at DESC);

CREATE INDEX idx_ai_requests_trip_status
    ON ai_action_requests(trip_id, status);


COMMIT;


-- ============================================================
-- VERIFICACIÓN
-- Al finalizar, esta consulta muestra las tablas creadas.
-- ============================================================

SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_type = 'BASE TABLE'
ORDER BY table_name;
