-- WayraPass D3: migración segura para bases existentes.
-- No se asigna una capacidad ficticia. Antes de ejecutar el bloque final,
-- el administrador debe definir capacity explícitamente para cada ruta.

ALTER TABLE routes
    ADD COLUMN IF NOT EXISTS capacity INTEGER;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM routes WHERE capacity IS NULL) THEN
        RAISE EXCEPTION
            'Defina routes.capacity (> 0) para todas las rutas antes de finalizar la migración D3';
    END IF;
END
$$;

ALTER TABLE routes
    ALTER COLUMN capacity SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'routes'::regclass
          AND conname = 'chk_route_capacity'
    ) THEN
        ALTER TABLE routes
            ADD CONSTRAINT chk_route_capacity CHECK (capacity > 0);
    END IF;
END
$$;
