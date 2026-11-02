-- WayraPass - dataset universitario de Lima Metropolitana.
-- PostgreSQL 17. Requiere wayrapass_db.sql y migraciones vigentes.
--
-- Decisiones de diseño:
-- * cada campus es una fila independiente en institutions;
-- * el esquema no guarda coordenadas en institutions: el stop final de cada
--   campus es su representación georreferenciada;
-- * el dataset administrado se identifica por las claves naturales documentadas
--   aquí (institution name/campus_name, stop name y route code);
-- * los IDs operativos existentes se conservan al transformar R-001/R-002/R-003.

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. Campus operativos (26)
-- ---------------------------------------------------------------------------
INSERT INTO institutions (name, campus_name, address, district, active)
SELECT * FROM (VALUES
    ('UPC', 'Monterrico', 'Prolongación Primavera 2390', 'Santiago de Surco', TRUE),
    ('UPC', 'San Miguel', 'Av. La Marina 2810', 'San Miguel', TRUE),
    ('UPC', 'San Isidro', 'Av. General Salaverry 2255', 'San Isidro', TRUE),
    ('UPC', 'Villa', 'Av. Alameda San Marcos 11', 'Chorrillos', TRUE),
    ('Universidad del Pacífico', 'Jesús María', 'Jr. Sánchez Cerro 2141', 'Jesús María', TRUE),
    ('UNMSM', 'Ciudad Universitaria', 'Av. Carlos Germán Amezaga 375', 'Lima', TRUE),
    ('UNMSM', 'San Fernando', 'Av. Miguel Grau 755', 'Lima', TRUE),
    ('UNMSM', 'Medicina Veterinaria', 'Av. Circunvalación 2800', 'San Borja', TRUE),
    ('UNMSM', 'Ingeniería Agroindustrial', 'Av. Wiesse km 10.5', 'San Juan de Lurigancho', TRUE),
    ('UNI', 'Rímac', 'Av. Túpac Amaru 210', 'Rímac', TRUE),
    ('Universidad de Lima', 'Monterrico', 'Av. Javier Prado Este 4600', 'Santiago de Surco', TRUE),
    ('Universidad de Lima', 'Mayorazgo', 'Av. Las Palmeras cdra. 5, Urb. Mayorazgo', 'Ate', TRUE),
    ('UPN', 'Ate', 'Av. Nicolás Ayllón 3132', 'Ate', TRUE),
    ('UPN', 'Breña', 'Av. Tingo María 1122', 'Breña', TRUE),
    ('UPN', 'Chorrillos', 'Av. Guardia Peruana 890', 'Chorrillos', TRUE),
    ('UPN', 'Comas', 'Av. Andrés Belaúnde, cuadra 10 s/n', 'Comas', TRUE),
    ('UPN', 'Los Olivos', 'Av. Alfredo Mendiola 6062', 'Los Olivos', TRUE),
    ('UPN', 'San Juan de Lurigancho', 'Av. El Sol 461', 'San Juan de Lurigancho', TRUE),
    ('UPN', 'Lima Sur', 'Av. Pedro Miotta 130', 'San Juan de Miraflores', TRUE),
    ('UCV', 'Ate', 'Carretera Central km 8.2', 'Ate', TRUE),
    ('UCV', 'Los Olivos', 'Av. Alfredo Mendiola 6232', 'Los Olivos', TRUE),
    ('UCV', 'San Juan de Lurigancho', 'Av. Del Parque 640, Urb. Canto Rey', 'San Juan de Lurigancho', TRUE),
    ('UCV', 'Lima Centro', 'Av. Argentina 1118', 'Lima', TRUE),
    ('UTEC', 'Barranco', 'Jr. Medrano Silva 165', 'Barranco', TRUE),
    ('PUCP', 'San Miguel', 'Av. Universitaria 1801', 'San Miguel', TRUE),
    ('UNALM', 'La Molina', 'Av. La Molina 2050', 'La Molina', TRUE)
) AS x(name, campus_name, address, district, active)
ON CONFLICT (name, campus_name) DO UPDATE
SET address = EXCLUDED.address,
    district = EXCLUDED.district,
    active = TRUE;

-- Antes de retirar Universidad Wayra, reasignar todas sus dependencias. Esto
-- preserva cuentas STUDENT, rutas manuales como P2-TMP y cualquier FK futura.
UPDATE students s
SET institution_id = target.id
FROM institutions old_i, institutions target
WHERE s.institution_id = old_i.id
  AND old_i.name = 'Universidad Wayra'
  AND old_i.campus_name = 'Campus Central'
  AND target.name = 'UPC' AND target.campus_name = 'Monterrico';

UPDATE routes r
SET institution_id = target.id,
    updated_at = CURRENT_TIMESTAMP
FROM institutions old_i, institutions target
WHERE r.institution_id = old_i.id
  AND old_i.name = 'Universidad Wayra'
  AND old_i.campus_name = 'Campus Central'
  AND target.name = 'UPC' AND target.campus_name = 'Monterrico';

DELETE FROM institutions
WHERE name = 'Universidad Wayra' AND campus_name = 'Campus Central'
  AND NOT EXISTS (SELECT 1 FROM students s WHERE s.institution_id = institutions.id)
  AND NOT EXISTS (SELECT 1 FROM routes r WHERE r.institution_id = institutions.id);

-- ---------------------------------------------------------------------------
-- 2. Catálogo georreferenciado de Lima.
-- Cada distrito conserva exactamente tres pickups públicos activos (129 en total).
-- Los campus se mantienen adicionalmente como destinos terminales y no cuentan
-- dentro de esos tres pickups. Las coordenadas son WGS84.
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE wp_lima_stops (
    name VARCHAR(150) PRIMARY KEY,
    address VARCHAR(250) NOT NULL,
    district VARCHAR(100) NOT NULL,
    latitude NUMERIC(9,6) NOT NULL,
    longitude NUMERIC(9,6) NOT NULL
) ON COMMIT DROP;

INSERT INTO wp_lima_stops VALUES
('Plaza de Armas de Ancón','Malecón Ferreyros / Jr. Abtao','Ancón',-11.773565,-77.176238),
('Óvalo de Ancón','Panamericana Norte / Av. Julio C. Tello','Ancón',-11.761963,-77.168105),
('Balneario de Ancón','Malecón Pardo','Ancón',-11.775824,-77.176982),
('Campus Universidad de Lima Mayorazgo','Av. Las Palmeras cdra. 5, Urb. Mayorazgo','Ate',-12.065206,-76.937861),
('Campus UPN Ate','Av. Nicolás Ayllón 3132','Ate',-12.055505,-76.942259),
('Campus UCV Ate','Carretera Central km 8.2','Ate',-12.027886,-76.919947),
('Plaza de Armas de Vitarte','Av. Nicolás Ayllón / Jr. Cuzco','Ate',-12.026740,-76.918130),
('Mercado Ceres','Av. Metropolitana / Av. José Carlos Mariátegui','Ate',-12.038600,-76.944400),
('Real Plaza Puruchuco','Av. Nicolás Ayllón 4770','Ate',-12.041400,-76.932100),
('Plaza de Barranco','Av. Pedro de Osma / Jr. Domeyer','Barranco',-12.149128,-77.021960),
('Estación Bulevar','Av. República de Panamá / Av. Bolognesi','Barranco',-12.147555,-77.020733),
('Campus UTEC Barranco','Jr. Medrano Silva 165','Barranco',-12.135550,-77.022139),
('Estación Balta','Av. República de Panamá / Av. Balta','Barranco',-12.143500,-77.020600),
('Plaza Bolognesi Breña','Av. Brasil / Av. Arica','Breña',-12.059988,-77.042536),
('Campus UPN Breña','Av. Tingo María 1122','Breña',-12.060780,-77.050381),
('Mercado Las Flores Breña','Jr. Napo / Jr. Pariacoto','Breña',-12.054381,-77.052641),
('Centro Comercial La Rambla Brasil','Av. Brasil 714','Breña',-12.062600,-77.046500),
('Óvalo de Carabayllo','Av. Túpac Amaru / Av. Universitaria','Carabayllo',-11.888091,-77.027326),
('Municipalidad de Carabayllo','Av. Túpac Amaru 1733','Carabayllo',-11.890613,-77.024986),
('Parque Zonal Manco Cápac','Av. Túpac Amaru km 18','Carabayllo',-11.873408,-77.022843),
('Parque Central de Chaclacayo','Av. Nicolás Ayllón / Los Cedros','Chaclacayo',-11.975486,-76.768199),
('Estadio Tahuantinsuyo Chaclacayo','Av. Nicolás Ayllón','Chaclacayo',-11.978885,-76.771542),
('Ñaña - Carretera Central','Carretera Central / Av. Bernard Balaguer','Chaclacayo',-11.987195,-76.835991),
('Campus UPC Villa','Av. Alameda San Marcos 11','Chorrillos',-12.198918,-77.009689),
('Campus UPN Chorrillos','Av. Guardia Peruana 890','Chorrillos',-12.179393,-77.000957),
('Estación Matellini','Av. Prolongación Paseo de la República','Chorrillos',-12.172426,-77.012785),
('Centro Comercial Plaza Lima Sur','Av. Prolongación Paseo de la República s/n','Chorrillos',-12.172900,-77.013300),
('Municipalidad de Chorrillos','Av. Defensores del Morro 550','Chorrillos',-12.168400,-77.025000),
('Plaza de Armas de Cieneguilla','Av. Nueva Toledo','Cieneguilla',-12.090181,-76.775685),
('Óvalo de Cieneguilla','Av. Nueva Toledo / Av. San Martín','Cieneguilla',-12.087381,-76.799123),
('Puente Panquilma','Av. Nueva Toledo / río Lurín','Cieneguilla',-12.117368,-76.812616),
('Campus UPN Comas','Av. Andrés Belaúnde, cuadra 10 s/n','Comas',-11.932388,-77.048744),
('Mallplaza Comas','Av. Los Ángeles / Universitaria','Comas',-11.938151,-77.059746),
('Estación Naranjal','Av. Túpac Amaru / Av. Chinchaysuyo','Comas',-11.981724,-77.059077),
('Plaza de Armas de Comas','Av. Túpac Amaru / Av. España','Comas',-11.944500,-77.062000),
('Municipalidad de El Agustino','Av. Riva Agüero 1358','El Agustino',-12.040364,-77.001448),
('Puente Nuevo','Av. Riva Agüero / Vía de Evitamiento','El Agustino',-12.041926,-76.986771),
('Hospital Hipólito Unanue','Av. César Vallejo 1390','El Agustino',-12.042190,-76.991232),
('Plaza Norte','Av. Tomás Valle / Panamericana Norte','Independencia',-12.006117,-77.059188),
('Estación Independencia','Av. Túpac Amaru / Av. Los Pinos','Independencia',-11.990889,-77.054917),
('MegaPlaza','Av. Alfredo Mendiola 3698','Independencia',-11.994208,-77.061397),
('Campo de Marte','Av. de la Peruanidad','Jesús María',-12.067735,-77.036442),
('Residencial San Felipe','Av. Gregorio Escobedo / Av. Salaverry','Jesús María',-12.089638,-77.054242),
('Campus Universidad del Pacífico','Jr. Sánchez Cerro 2141','Jesús María',-12.084747,-77.048753),
('Real Plaza Salaverry','Av. General Salaverry 2370','Jesús María',-12.089600,-77.052100),
('Campus UNALM La Molina','Av. La Molina 2050','La Molina',-12.082399,-76.945301),
('Óvalo La Fontana','Av. La Molina / Av. Javier Prado','La Molina',-12.073746,-76.956622),
('Molicentro','Av. La Molina 1167','La Molina',-12.088024,-76.949463),
('Municipalidad de La Molina','Av. Ricardo Elías Aparicio 740','La Molina',-12.078200,-76.916800),
('Plaza Manco Cápac','Av. Manco Cápac / Av. Iquitos','La Victoria',-12.064965,-77.030208),
('Estación Gamarra','Av. Aviación / Jr. Hipólito Unanue','La Victoria',-12.064716,-77.012234),
('Estadio Alejandro Villanueva','Jr. Abtao 130','La Victoria',-12.068192,-77.022382),
('Campus UNMSM Ciudad Universitaria','Av. Carlos Germán Amezaga 375','Lima',-12.056096,-77.084084),
('Campus UNMSM San Fernando','Av. Miguel Grau 755','Lima',-12.057663,-77.022177),
('Campus UCV Lima Centro','Av. Argentina 1118','Lima',-12.044725,-77.053623),
('Plaza San Martín','Av. Nicolás de Piérola / Jr. de la Unión','Lima',-12.051700,-77.034700),
('Plaza Mayor de Lima','Jr. de la Unión / Jr. Huallaga','Lima',-12.045300,-77.030900),
('Parque Mariscal Castilla','Av. César Vallejo / Jr. Pedro Conde','Lince',-12.083819,-77.038879),
('Centro Comercial Risso','Av. Arequipa 2250','Lince',-12.084829,-77.036793),
('Hospital Rebagliati','Av. Edgardo Rebagliati 490','Lince',-12.078004,-77.040235),
('Campus UPN Los Olivos','Av. Alfredo Mendiola 6062','Los Olivos',-11.959441,-77.067398),
('Campus UCV Los Olivos','Av. Alfredo Mendiola 6232','Los Olivos',-11.957641,-77.068230),
('Municipalidad de Los Olivos','Av. Carlos Izaguirre 813','Los Olivos',-11.990036,-77.070160),
('Mercado Covida','Av. Antúnez de Mayolo / Av. Universitaria','Los Olivos',-11.987000,-77.072400),
('Parque Zonal Lloque Yupanqui','Av. Universitaria / Av. Naranjal','Los Olivos',-11.980400,-77.076900),
('Parque Central de Chosica','Jr. Trujillo / Jr. Chiclayo','Lurigancho',-11.935761,-76.697142),
('Paradero La Cantuta','Av. Enrique Guzmán y Valle, acceso principal','Lurigancho',-11.947933,-76.701647),
('Estación Chosica','Av. 28 de Julio / Jr. Callao','Lurigancho',-11.938760,-76.708681),
('Plaza de Armas de Lurín','Jr. Bolognesi / Jr. Castilla','Lurín',-12.273848,-76.869882),
('Puente Lurín','Antigua Panamericana Sur / río Lurín','Lurín',-12.266445,-76.879521),
('Mall del Sur Lurín','Panamericana Sur km 33','Lurín',-12.247624,-76.912733),
('Parque Túpac Amaru','Av. Brasil / Jr. Bolognesi','Magdalena del Mar',-12.091762,-77.070249),
('Mercado de Magdalena','Jr. José Gálvez / Jr. Leoncio Prado','Magdalena del Mar',-12.091300,-77.072384),
('Av. Brasil / Javier Prado','Av. Brasil / Av. Javier Prado Oeste','Magdalena del Mar',-12.093181,-77.061353),
('Parque Kennedy','Av. Larco / Av. Diagonal','Miraflores',-12.121085,-77.029654),
('Óvalo Miraflores','Av. Arequipa / Av. Pardo','Miraflores',-12.119269,-77.029055),
('Larcomar','Malecón de la Reserva 610','Miraflores',-12.131565,-77.030791),
('Plaza de Armas de Pachacámac','Jr. Comercio / Jr. Paraíso','Pachacámac',-12.229467,-76.859677),
('Manchay - Portada I','Av. Víctor Malásquez / Av. Manchay','Pachacámac',-12.103174,-76.872182),
('Santuario de Pachacámac','Antigua Panamericana Sur km 31.5','Pachacámac',-12.256029,-76.900465),
('Plaza de Armas de Pucusana','Jr. Lima / Malecón San Martín','Pucusana',-12.482949,-76.797042),
('Óvalo de Pucusana','Panamericana Sur km 57','Pucusana',-12.469567,-76.787099),
('Mercado Municipal de Pucusana','Av. Lima / Jr. Callao','Pucusana',-12.481203,-76.795108),
('Plaza Bolívar Pueblo Libre','Av. Bolívar / Av. General Vivanco','Pueblo Libre',-12.077641,-77.062173),
('Museo Larco','Av. Simón Bolívar 1515','Pueblo Libre',-12.072315,-77.070827),
('Av. Brasil / Bolívar','Av. Brasil / Av. Simón Bolívar','Pueblo Libre',-12.070094,-77.057104),
('Óvalo de Puente Piedra','Panamericana Norte / Av. Juan Lecaros','Puente Piedra',-11.866073,-77.077681),
('Plaza de Armas de Puente Piedra','Av. Sáenz Peña / Jr. Lecaros','Puente Piedra',-11.864309,-77.075375),
('Mercado Huamantanga','Av. San Juan de Dios','Puente Piedra',-11.855610,-77.083832),
('Playa Caballeros','Antigua Panamericana Sur km 35','Punta Hermosa',-12.323629,-76.826602),
('Municipalidad de Punta Hermosa','Av. Sunset / Malecón Central','Punta Hermosa',-12.333174,-76.824524),
('Óvalo Punta Hermosa','Panamericana Sur km 43','Punta Hermosa',-12.337431,-76.805758),
('Plaza de Armas de Punta Negra','Av. San José / Jr. Lima','Punta Negra',-12.365897,-76.795033),
('Playa Punta Rocas','Antigua Panamericana Sur km 45','Punta Negra',-12.350769,-76.811329),
('Óvalo Punta Negra','Panamericana Sur km 46','Punta Negra',-12.369243,-76.782021),
('Campus UNI Rímac','Av. Túpac Amaru 210','Rímac',-12.023658,-77.047585),
('Plaza de Acho','Jr. Hualgayoc / Marañón','Rímac',-12.041354,-77.023551),
('Alameda de los Descalzos','Jr. Manco Cápac / Alameda','Rímac',-12.033251,-77.027658),
('Plaza de Armas del Rímac','Jr. Trujillo / Jr. Chiclayo','Rímac',-12.030800,-77.030200),
('Plaza de Armas de San Bartolo','Av. Mar Pacífico / Av. San José','San Bartolo',-12.390551,-76.779745),
('Mercado Municipal San Bartolo','Av. San Bartolo / Jr. Lima','San Bartolo',-12.388813,-76.780985),
('Playa Norte San Bartolo','Malecón José de San Martín','San Bartolo',-12.383272,-76.784526),
('Campus UNMSM Veterinaria','Av. Circunvalación 2800','San Borja',-12.081674,-76.987948),
('Estación La Cultura','Av. Aviación / Av. Javier Prado','San Borja',-12.086956,-77.003357),
('Pentagonito','Av. San Borja Norte / Av. Boulevard','San Borja',-12.098268,-76.996328),
('Real Plaza Primavera','Av. Angamos Este 2681','San Borja',-12.111300,-76.990300),
('Campus UPC San Isidro','Av. General Salaverry 2255','San Isidro',-12.097573,-77.057136),
('Centro Comercial Camino Real','Av. Camino Real 456','San Isidro',-12.097060,-77.036494),
('Javier Prado / Rivera Navarrete','Av. Javier Prado / Av. Rivera Navarrete','San Isidro',-12.092310,-77.028162),
('Parque El Olivar','Av. Los Incas / Calle La República','San Isidro',-12.098800,-77.035800),
('Campus UNMSM Agroindustrial SJL','Av. Wiesse km 10.5','San Juan de Lurigancho',-11.946180,-76.988071),
('Campus UPN San Juan de Lurigancho','Av. El Sol 461','San Juan de Lurigancho',-12.009295,-77.005412),
('Campus UCV San Juan de Lurigancho','Av. Del Parque 640, Urb. Canto Rey','San Juan de Lurigancho',-11.969206,-77.002398),
('Estación Bayóvar','Av. Fernando Wiesse / Av. Héroes del Cenepa','San Juan de Lurigancho',-11.959700,-76.996200),
('Estación Los Jardines','Av. Próceres de la Independencia / Av. Los Jardines','San Juan de Lurigancho',-12.000200,-77.006600),
('Mall Aventura San Juan de Lurigancho','Av. Lurigancho 997','San Juan de Lurigancho',-12.016900,-77.012700),
('Campus UPN Lima Sur','Av. Pedro Miotta 130','San Juan de Miraflores',-12.157253,-76.971278),
('Mall del Sur','Av. Los Lirios / Panamericana Sur','San Juan de Miraflores',-12.155616,-76.981032),
('Estación Atocongo','Av. Los Héroes / Av. Buckingham','San Juan de Miraflores',-12.150824,-76.981425),
('Mercado Ciudad de Dios','Av. Los Héroes / Av. San Juan','San Juan de Miraflores',-12.158200,-76.972600),
('La Videna','Av. Canadá / Av. Aviación','San Luis',-12.082403,-76.999254),
('Mercado de Frutas','Av. Nicolás Arriola 2400','San Luis',-12.066919,-76.994433),
('Av. Canadá / San Luis','Av. Canadá / Av. San Luis','San Luis',-12.075554,-76.994879),
('Parque Zonal Mayta Cápac','Av. Angélica Gamarra / Universitaria','San Martín de Porres',-12.001497,-77.080611),
('Av. Túpac Amaru / Av. Honorio Delgado','Intersección de Av. Túpac Amaru con Av. Honorio Delgado','San Martín de Porres',-12.023185,-77.056197),
('Av. Perú / Dueñas','Av. Perú / Av. Nicolás Dueñas','San Martín de Porres',-12.032202,-77.069057),
('Campus UPC San Miguel','Av. La Marina 2810','San Miguel',-12.077843,-77.096198),
('Campus PUCP San Miguel','Av. Universitaria 1801','San Miguel',-12.069507,-77.079359),
('Plaza San Miguel','Av. La Marina 2000','San Miguel',-12.076643,-77.082299),
('Av. La Marina / Universitaria','Av. La Marina / Av. Universitaria','San Miguel',-12.076100,-77.083700),
('Parque Media Luna','Av. Bertolotto / Malecón Bertolotto','San Miguel',-12.087200,-77.086400),
('Mall Aventura Santa Anita','Carretera Central / Av. Ruiseñores','Santa Anita',-12.055436,-76.971326),
('Óvalo Santa Anita','Carretera Central / Av. La Molina','Santa Anita',-12.057815,-76.957564),
('Municipalidad de Santa Anita','Av. Los Eucaliptos cdra. 12','Santa Anita',-12.043238,-76.970776),
('Plaza de Santa María del Mar','Av. Terramar / Av. El Sardinero','Santa María del Mar',-12.407515,-76.778181),
('Playa Santa María','Malecón de Santa María','Santa María del Mar',-12.409773,-76.780735),
('Acceso Santa María','Panamericana Sur km 50','Santa María del Mar',-12.402941,-76.763552),
('Plaza de Armas de Santa Rosa','Av. Bertello / Av. Alejandro Bertello','Santa Rosa',-11.799434,-77.173446),
('Óvalo Santa Rosa','Panamericana Norte / Av. Bertello','Santa Rosa',-11.818585,-77.159379),
('Balneario Santa Rosa','Av. Costanera / Malecón','Santa Rosa',-11.787662,-77.183358),
('Campus UPC Monterrico','Prolongación Primavera 2390','Santiago de Surco',-12.104020,-76.962726),
('Campus Universidad de Lima Monterrico','Av. Javier Prado Este 4600','Santiago de Surco',-12.084940,-76.971206),
('Óvalo Higuereta','Av. Benavides / Av. Aviación','Santiago de Surco',-12.129134,-77.000382),
('Centro Comercial Jockey Plaza','Av. Javier Prado Este 4200','Santiago de Surco',-12.086500,-76.976000),
('Parque de la Amistad','Av. Caminos del Inca 22','Santiago de Surco',-12.129500,-76.984700),
('Mercado N.º 1 de Surquillo','Jr. Narciso de la Colina 1050','Surquillo',-12.117121,-77.022639),
('Angamos / República de Panamá','Av. Angamos Este / Av. República de Panamá','Surquillo',-12.113323,-77.017658),
('Parque Reducto N.º 5','Av. Andrés Avelino Cáceres','Surquillo',-12.110401,-77.009972),
('Parque Zonal Huáscar','Av. 200 Millas / Revolución','Villa El Salvador',-12.221107,-76.936144),
('Estación Villa El Salvador','Av. Separadora Industrial / Velasco Alvarado','Villa El Salvador',-12.207420,-76.939553),
('Municipalidad de Villa El Salvador','Av. Revolución / César Vallejo','Villa El Salvador',-12.213888,-76.938877),
('Hospital María Auxiliadora','Av. Miguel Iglesias 968','Villa María del Triunfo',-12.161472,-76.958883),
('Parque Zonal Flor de Amancaes','Av. 26 de Noviembre','Villa María del Triunfo',-12.169290,-76.934817),
('Municipalidad de Villa María del Triunfo','Av. Salvador Allende 359','Villa María del Triunfo',-12.162773,-76.944301);

-- Microcorrección conceptual: conservar el mismo ID y ubicación física del
-- antiguo stop universitario, pero modelarlo como la intersección pública que
-- sirve al corredor norte. Así no se crean filas adicionales ni se rompen FK.
UPDATE stops
SET name = 'Av. Túpac Amaru / Av. Honorio Delgado',
    address = 'Intersección de Av. Túpac Amaru con Av. Honorio Delgado',
    district = 'San Martín de Porres',
    latitude = -12.023185,
    longitude = -77.056197,
    active = TRUE
WHERE name = 'Universidad Cayetano Heredia'
  AND NOT EXISTS (
      SELECT 1 FROM stops existing
      WHERE existing.name = 'Av. Túpac Amaru / Av. Honorio Delgado'
  );

-- La antigua referencia a la universidad se conserva como el paradero público
-- ubicado en su acceso principal, manteniendo el ID y cualquier FK existente.
UPDATE stops
SET name = 'Paradero La Cantuta',
    address = 'Av. Enrique Guzmán y Valle, acceso principal',
    district = 'Lurigancho',
    latitude = -11.947933,
    longitude = -76.701647,
    active = TRUE
WHERE name = 'Universidad La Cantuta'
  AND NOT EXISTS (
      SELECT 1 FROM stops existing
      WHERE existing.name = 'Paradero La Cantuta'
  );

-- No existe constraint natural en stops: insertar por ausencia y luego actualizar.
INSERT INTO stops (name, address, district, latitude, longitude, active)
SELECT w.name, w.address, w.district, w.latitude, w.longitude, TRUE
FROM wp_lima_stops w
WHERE NOT EXISTS (SELECT 1 FROM stops s WHERE s.name = w.name);

UPDATE stops s
SET address = w.address,
    district = w.district,
    latitude = w.latitude,
    longitude = w.longitude,
    active = TRUE
FROM wp_lima_stops w
WHERE s.name = w.name;

-- ---------------------------------------------------------------------------
-- 3. Transformación segura de las tres rutas demo que tienen dependencias.
-- ---------------------------------------------------------------------------
UPDATE routes r SET
    institution_id = i.id, code = 'UPC-MON-SMI-01',
    name = 'San Miguel a UPC Monterrico', origin = 'Plaza San Miguel',
    destination = 'UPC Monterrico',
    description = 'Corredor San Miguel - Pueblo Libre - Surco.',
    capacity = 32, status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP
FROM institutions i
WHERE r.code = 'R-001' AND i.name = 'UPC' AND i.campus_name = 'Monterrico'
  AND NOT EXISTS (SELECT 1 FROM routes x WHERE x.code = 'UPC-MON-SMI-01');

UPDATE routes r SET
    institution_id = i.id, code = 'PUCP-NOR-01',
    name = 'Lima Norte a PUCP', origin = 'Plaza Norte',
    destination = 'PUCP San Miguel',
    description = 'Corredor Independencia - SMP - San Miguel.',
    capacity = 24, status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP
FROM institutions i
WHERE r.code = 'R-002' AND i.name = 'PUCP' AND i.campus_name = 'San Miguel'
  AND NOT EXISTS (SELECT 1 FROM routes x WHERE x.code = 'PUCP-NOR-01');

UPDATE routes r SET
    institution_id = i.id, code = 'UNI-EST-01',
    name = 'Lima Este a UNI', origin = 'Mall Aventura Santa Anita',
    destination = 'UNI Rímac',
    description = 'Corredor Santa Anita - El Agustino - Rímac.',
    capacity = 40, status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP
FROM institutions i
WHERE r.code = 'R-003' AND i.name = 'UNI' AND i.campus_name = 'Rímac'
  AND NOT EXISTS (SELECT 1 FROM routes x WHERE x.code = 'UNI-EST-01');

-- Stage de mappings válidos (órdenes altos para no colisionar con la demo).
INSERT INTO route_stops (route_id, stop_id, stop_order, estimated_minutes_from_start, active)
SELECT r.id, s.id, x.ord, x.minutes, TRUE
FROM (VALUES
 ('UPC-MON-SMI-01','Plaza San Miguel',101,0),
 ('UPC-MON-SMI-01','Campus PUCP San Miguel',102,8),
 ('UPC-MON-SMI-01','Óvalo Higuereta',103,34),
 ('UPC-MON-SMI-01','Campus UPC Monterrico',104,45),
 ('PUCP-NOR-01','Plaza Norte',101,0),
 ('PUCP-NOR-01','Av. Túpac Amaru / Av. Honorio Delgado',102,18),
 ('PUCP-NOR-01','Av. Perú / Dueñas',103,30),
 ('PUCP-NOR-01','Campus PUCP San Miguel',104,55),
 ('UNI-EST-01','Mall Aventura Santa Anita',101,0),
 ('UNI-EST-01','Hospital Hipólito Unanue',102,20),
 ('UNI-EST-01','Plaza de Acho',103,39),
 ('UNI-EST-01','Campus UNI Rímac',104,50)
) AS x(code, stop_name, ord, minutes)
JOIN routes r ON r.code = x.code
JOIN stops s ON s.name = x.stop_name
WHERE EXISTS (SELECT 1 FROM stops old_s WHERE old_s.name LIKE 'WP Demo - %')
ON CONFLICT (route_id, stop_id) DO NOTHING;

-- Las matrículas conservan historial/route_id, pero pasan a un stop de la ruta nueva.
UPDATE student_route_enrollments e
SET stop_id = chosen.stop_id
FROM routes r
JOIN LATERAL (
    SELECT rs.stop_id FROM route_stops rs
    WHERE rs.route_id = r.id AND rs.stop_order >= 100
    ORDER BY rs.stop_order LIMIT 1
) chosen ON TRUE
WHERE e.route_id = r.id
  AND r.code IN ('UPC-MON-SMI-01','PUCP-NOR-01','UNI-EST-01')
  AND EXISTS (
      SELECT 1 FROM stops old_s
      WHERE old_s.id = e.stop_id AND old_s.name LIKE 'WP Demo - %'
  );

-- Boardings no tiene FK compuesta; se mueve cada evento demo al primer stop real
-- de la ruta de su viaje para no dejar referencias a paraderos ficticios.
UPDATE boardings b
SET stop_id = chosen.stop_id
FROM trips t
JOIN routes r ON r.id = t.route_id
JOIN LATERAL (
    SELECT rs.stop_id FROM route_stops rs
    WHERE rs.route_id = r.id AND rs.stop_order >= 100
    ORDER BY rs.stop_order LIMIT 1
) chosen ON TRUE
WHERE b.trip_id = t.id
  AND r.code IN ('UPC-MON-SMI-01','PUCP-NOR-01','UNI-EST-01')
  AND EXISTS (
      SELECT 1 FROM stops old_s
      WHERE old_s.id = b.stop_id AND old_s.name LIKE 'WP Demo - %'
  );

DELETE FROM route_stops rs
USING stops s
WHERE rs.stop_id = s.id AND s.name LIKE 'WP Demo - %';

DELETE FROM stops s
WHERE s.name LIKE 'WP Demo - %'
  AND NOT EXISTS (SELECT 1 FROM route_stops rs WHERE rs.stop_id = s.id)
  AND NOT EXISTS (SELECT 1 FROM student_route_enrollments e WHERE e.stop_id = s.id)
  AND NOT EXISTS (SELECT 1 FROM boardings b WHERE b.stop_id = s.id);

-- ---------------------------------------------------------------------------
-- 4. Catálogo de rutas (30 rutas, al menos una por campus).
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE wp_lima_routes (
    code VARCHAR(30) PRIMARY KEY,
    institution_name VARCHAR(150) NOT NULL,
    campus_name VARCHAR(150) NOT NULL,
    route_name VARCHAR(150) NOT NULL,
    origin VARCHAR(200) NOT NULL,
    destination VARCHAR(200) NOT NULL,
    description VARCHAR(500) NOT NULL,
    capacity INTEGER NOT NULL,
    duration_minutes INTEGER NOT NULL
) ON COMMIT DROP;

INSERT INTO wp_lima_routes VALUES
('UPC-MON-SMI-01','UPC','Monterrico','San Miguel a UPC Monterrico','Plaza San Miguel','UPC Monterrico','Corredor La Marina - Surco.',32,45),
('UPC-MON-MIR-01','UPC','Monterrico','Miraflores a UPC Monterrico','Parque Kennedy','UPC Monterrico','Corredor Miraflores - Higuereta - Primavera.',32,38),
('UPC-MON-LMO-01','UPC','Monterrico','Santa Anita y La Molina a UPC Monterrico','Municipalidad de Santa Anita','UPC Monterrico','Corredor Santa Anita - La Molina - Primavera.',32,35),
('UPC-SMI-PUE-01','UPC','San Miguel','Pueblo Libre a UPC San Miguel','Plaza Bolívar Pueblo Libre','UPC San Miguel','Corredor Pueblo Libre - La Marina.',32,28),
('UPC-SIS-JMA-01','UPC','San Isidro','Jesús María a UPC San Isidro','Campo de Marte','UPC San Isidro','Corredor Salaverry.',32,25),
('UPC-VIL-VES-01','UPC','Villa','Villa El Salvador a UPC Villa','Parque Zonal Huáscar','UPC Villa','Corredor VES - Matellini - Villa.',32,40),
('UP-JMA-LIN-01','Universidad del Pacífico','Jesús María','Lince a Universidad del Pacífico','Centro Comercial Risso','Universidad del Pacífico','Corredor Lince - Jesús María.',24,24),
('UNMSM-CU-CEN-01','UNMSM','Ciudad Universitaria','Centro a UNMSM Ciudad Universitaria','Plaza Bolognesi Breña','UNMSM Ciudad Universitaria','Corredor Breña - Pueblo Libre - Ciudad Universitaria.',40,35),
('UNMSM-SF-LVI-01','UNMSM','San Fernando','La Victoria a UNMSM San Fernando','Estación Gamarra','UNMSM San Fernando','Corredor La Victoria - Grau.',40,30),
('UNMSM-VET-SLU-01','UNMSM','Medicina Veterinaria','San Luis a UNMSM Veterinaria','Mercado de Frutas','UNMSM Veterinaria','Corredor San Luis - San Borja.',32,27),
('UNMSM-AGR-SJL-01','UNMSM','Ingeniería Agroindustrial','El Agustino a UNMSM Agroindustrial','Hospital Hipólito Unanue','UNMSM Agroindustrial','Corredor El Agustino - SJL.',32,46),
('UNI-NOR-01','UNI','Rímac','Lima Norte a UNI','Estación Independencia','UNI Rímac','Corredor Independencia - SMP - Rímac.',40,50),
('UNI-EST-01','UNI','Rímac','Lima Este a UNI','Mall Aventura Santa Anita','UNI Rímac','Corredor Santa Anita - El Agustino - Rímac.',40,50),
('ULIMA-MON-SBO-01','Universidad de Lima','Monterrico','San Isidro y San Borja a Universidad de Lima','Javier Prado / Rivera Navarrete','Universidad de Lima Monterrico','Corredor Javier Prado Este.',32,25),
('ULIMA-MAY-SAN-01','Universidad de Lima','Mayorazgo','El Agustino y Santa Anita a Universidad de Lima Mayorazgo','Hospital Hipólito Unanue','Universidad de Lima Mayorazgo','Corredor El Agustino - Carretera Central - Mayorazgo.',32,32),
('UPN-ATE-SLU-01','UPN','Ate','San Luis a UPN Ate','Mercado de Frutas','UPN Ate','Corredor Nicolás Arriola - Carretera Central.',32,34),
('UPN-BRE-LIN-01','UPN','Breña','Lince a UPN Breña','Centro Comercial Risso','UPN Breña','Corredor Lince - Jesús María - Breña.',32,30),
('UPN-CHO-SJM-01','UPN','Chorrillos','San Juan de Miraflores a UPN Chorrillos','Estación Atocongo','UPN Chorrillos','Corredor Atocongo - Matellini - Chorrillos.',32,33),
('UPN-COM-NOR-01','UPN','Comas','Independencia a UPN Comas','Estación Independencia','UPN Comas','Corredor Túpac Amaru - Comas.',32,28),
('UPN-LOL-PPI-01','UPN','Los Olivos','Puente Piedra a UPN Los Olivos','Plaza de Armas de Puente Piedra','UPN Los Olivos','Corredor Panamericana Norte.',32,42),
('UPN-SJL-EAG-01','UPN','San Juan de Lurigancho','El Agustino a UPN SJL','Puente Nuevo','UPN San Juan de Lurigancho','Corredor Riva Agüero - El Sol.',32,38),
('UPN-LSU-VMT-01','UPN','Lima Sur','Villa María del Triunfo a UPN Lima Sur','Parque Zonal Flor de Amancaes','UPN Lima Sur','Corredor VMT - SJM.',32,31),
('UCV-ATE-CIE-01','UCV','Ate','Cieneguilla a UCV Ate','Plaza de Armas de Cieneguilla','UCV Ate','Corredor Nueva Toledo - Carretera Central.',32,52),
('UCV-LOL-SMP-01','UCV','Los Olivos','SMP a UCV Los Olivos','Av. Perú / Dueñas','UCV Los Olivos','Corredor SMP - Panamericana Norte.',32,36),
('UCV-SJL-RIM-01','UCV','San Juan de Lurigancho','Rímac a UCV SJL','Plaza de Acho','UCV San Juan de Lurigancho','Corredor Rímac - Próceres - Canto Rey.',32,42),
('UCV-CEN-CAL-01','UCV','Lima Centro','Jesús María a UCV Lima Centro','Campo de Marte','UCV Lima Centro','Corredor Brasil - Argentina.',32,34),
('UTEC-BAR-MIR-01','UTEC','Barranco','Surquillo y Miraflores a UTEC','Angamos / República de Panamá','UTEC Barranco','Corredor Surquillo - Costa Verde - Barranco.',24,26),
('PUCP-NOR-01','PUCP','San Miguel','Lima Norte a PUCP','Plaza Norte','PUCP San Miguel','Corredor Independencia - SMP - San Miguel.',24,55),
('PUCP-MAG-01','PUCP','San Miguel','Magdalena a PUCP','Parque Túpac Amaru','PUCP San Miguel','Corredor Magdalena - La Marina - Universitaria.',24,25),
('UNALM-EST-01','UNALM','La Molina','Santa Anita a UNALM','Mall Aventura Santa Anita','UNALM La Molina','Corredor Santa Anita - La Molina.',32,48);

INSERT INTO routes (institution_id, code, name, origin, destination,
                    description, capacity, status)
SELECT i.id, x.code, x.route_name, x.origin, x.destination,
       x.description, x.capacity, 'ACTIVE'
FROM wp_lima_routes x
JOIN institutions i
  ON i.name = x.institution_name AND i.campus_name = x.campus_name
ON CONFLICT (code) DO UPDATE
SET institution_id = EXCLUDED.institution_id,
    name = EXCLUDED.name,
    origin = EXCLUDED.origin,
    destination = EXCLUDED.destination,
    description = EXCLUDED.description,
    capacity = EXCLUDED.capacity,
    status = 'ACTIVE',
    updated_at = CURRENT_TIMESTAMP;

-- Los 4 puntos de cada ruta ya están ordenados y sus minutos son acumulados.
CREATE TEMP TABLE wp_lima_route_stops (
    route_code VARCHAR(30) NOT NULL,
    stop_name VARCHAR(150) NOT NULL,
    stop_order INTEGER NOT NULL,
    minutes INTEGER NOT NULL,
    PRIMARY KEY (route_code, stop_order),
    UNIQUE (route_code, stop_name)
) ON COMMIT DROP;

INSERT INTO wp_lima_route_stops VALUES
('UPC-MON-SMI-01','Plaza San Miguel',1,0),('UPC-MON-SMI-01','Av. Brasil / Bolívar',2,10),('UPC-MON-SMI-01','Óvalo Higuereta',3,34),('UPC-MON-SMI-01','Campus UPC Monterrico',4,45),
('UPC-MON-MIR-01','Parque Kennedy',1,0),('UPC-MON-MIR-01','Óvalo Miraflores',2,6),('UPC-MON-MIR-01','Óvalo Higuereta',3,25),('UPC-MON-MIR-01','Campus UPC Monterrico',4,38),
('UPC-MON-LMO-01','Municipalidad de Santa Anita',1,0),('UPC-MON-LMO-01','Óvalo Santa Anita',2,10),('UPC-MON-LMO-01','Molicentro',3,25),('UPC-MON-LMO-01','Campus UPC Monterrico',4,35),
('UPC-SMI-PUE-01','Plaza Bolívar Pueblo Libre',1,0),('UPC-SMI-PUE-01','Museo Larco',2,8),('UPC-SMI-PUE-01','Plaza San Miguel',3,18),('UPC-SMI-PUE-01','Campus UPC San Miguel',4,28),
('UPC-SIS-JMA-01','Campo de Marte',1,0),('UPC-SIS-JMA-01','Residencial San Felipe',2,10),('UPC-SIS-JMA-01','Av. Brasil / Javier Prado',3,18),('UPC-SIS-JMA-01','Campus UPC San Isidro',4,25),
('UPC-VIL-VES-01','Parque Zonal Huáscar',1,0),('UPC-VIL-VES-01','Municipalidad de Villa El Salvador',2,10),('UPC-VIL-VES-01','Estación Villa El Salvador',3,18),('UPC-VIL-VES-01','Campus UPC Villa',4,40),
('UP-JMA-LIN-01','Centro Comercial Risso',1,0),('UP-JMA-LIN-01','Parque Mariscal Castilla',2,7),('UP-JMA-LIN-01','Hospital Rebagliati',3,14),('UP-JMA-LIN-01','Campus Universidad del Pacífico',4,24),
('UNMSM-CU-CEN-01','Plaza Bolognesi Breña',1,0),('UNMSM-CU-CEN-01','Av. Brasil / Bolívar',2,12),('UNMSM-CU-CEN-01','Museo Larco',3,23),('UNMSM-CU-CEN-01','Campus UNMSM Ciudad Universitaria',4,35),
('UNMSM-SF-LVI-01','Estación Gamarra',1,0),('UNMSM-SF-LVI-01','Estadio Alejandro Villanueva',2,10),('UNMSM-SF-LVI-01','Plaza Manco Cápac',3,20),('UNMSM-SF-LVI-01','Campus UNMSM San Fernando',4,30),
('UNMSM-VET-SLU-01','Mercado de Frutas',1,0),('UNMSM-VET-SLU-01','Av. Canadá / San Luis',2,8),('UNMSM-VET-SLU-01','La Videna',3,16),('UNMSM-VET-SLU-01','Campus UNMSM Veterinaria',4,27),
('UNMSM-AGR-SJL-01','Municipalidad de El Agustino',1,0),('UNMSM-AGR-SJL-01','Hospital Hipólito Unanue',2,8),('UNMSM-AGR-SJL-01','Puente Nuevo',3,15),('UNMSM-AGR-SJL-01','Campus UNMSM Agroindustrial SJL',4,46),
('UNI-NOR-01','Estación Independencia',1,0),('UNI-NOR-01','Plaza Norte',2,10),('UNI-NOR-01','Av. Túpac Amaru / Av. Honorio Delgado',3,29),('UNI-NOR-01','Campus UNI Rímac',4,50),
('UNI-EST-01','Mall Aventura Santa Anita',1,0),('UNI-EST-01','Hospital Hipólito Unanue',2,20),('UNI-EST-01','Plaza de Acho',3,39),('UNI-EST-01','Campus UNI Rímac',4,50),
('ULIMA-MON-SBO-01','Javier Prado / Rivera Navarrete',1,0),('ULIMA-MON-SBO-01','Estación La Cultura',2,10),('ULIMA-MON-SBO-01','Pentagonito',3,16),('ULIMA-MON-SBO-01','Campus Universidad de Lima Monterrico',4,25),
('ULIMA-MAY-SAN-01','Hospital Hipólito Unanue',1,0),('ULIMA-MAY-SAN-01','Mall Aventura Santa Anita',2,10),('ULIMA-MAY-SAN-01','Óvalo Santa Anita',3,18),('ULIMA-MAY-SAN-01','Campus Universidad de Lima Mayorazgo',4,32),
('UPN-ATE-SLU-01','Mercado de Frutas',1,0),('UPN-ATE-SLU-01','Municipalidad de Santa Anita',2,12),('UPN-ATE-SLU-01','Óvalo Santa Anita',3,22),('UPN-ATE-SLU-01','Campus UPN Ate',4,34),
('UPN-BRE-LIN-01','Centro Comercial Risso',1,0),('UPN-BRE-LIN-01','Parque Mariscal Castilla',2,7),('UPN-BRE-LIN-01','Plaza Bolognesi Breña',3,20),('UPN-BRE-LIN-01','Campus UPN Breña',4,30),
('UPN-CHO-SJM-01','Estación Atocongo',1,0),('UPN-CHO-SJM-01','Mall del Sur',2,7),('UPN-CHO-SJM-01','Estación Matellini',3,22),('UPN-CHO-SJM-01','Campus UPN Chorrillos',4,33),
('UPN-COM-NOR-01','Estación Independencia',1,0),('UPN-COM-NOR-01','Estación Naranjal',2,8),('UPN-COM-NOR-01','Mallplaza Comas',3,18),('UPN-COM-NOR-01','Campus UPN Comas',4,28),
('UPN-LOL-PPI-01','Plaza de Armas de Puente Piedra',1,0),('UPN-LOL-PPI-01','Óvalo de Puente Piedra',2,8),('UPN-LOL-PPI-01','Municipalidad de Los Olivos',3,31),('UPN-LOL-PPI-01','Campus UPN Los Olivos',4,42),
('UPN-SJL-EAG-01','Puente Nuevo',1,0),('UPN-SJL-EAG-01','Hospital Hipólito Unanue',2,6),('UPN-SJL-EAG-01','Municipalidad de El Agustino',3,12),('UPN-SJL-EAG-01','Campus UPN San Juan de Lurigancho',4,38),
('UPN-LSU-VMT-01','Parque Zonal Flor de Amancaes',1,0),('UPN-LSU-VMT-01','Municipalidad de Villa María del Triunfo',2,8),('UPN-LSU-VMT-01','Hospital María Auxiliadora',3,16),('UPN-LSU-VMT-01','Campus UPN Lima Sur',4,31),
('UCV-ATE-CIE-01','Plaza de Armas de Cieneguilla',1,0),('UCV-ATE-CIE-01','Puente Panquilma',2,15),('UCV-ATE-CIE-01','Óvalo de Cieneguilla',3,25),('UCV-ATE-CIE-01','Campus UCV Ate',4,52),
('UCV-LOL-SMP-01','Av. Perú / Dueñas',1,0),('UCV-LOL-SMP-01','Av. Túpac Amaru / Av. Honorio Delgado',2,12),('UCV-LOL-SMP-01','Municipalidad de Los Olivos',3,28),('UCV-LOL-SMP-01','Campus UCV Los Olivos',4,36),
('UCV-SJL-RIM-01','Plaza de Acho',1,0),('UCV-SJL-RIM-01','Municipalidad de El Agustino',2,12),('UCV-SJL-RIM-01','Hospital Hipólito Unanue',3,18),('UCV-SJL-RIM-01','Campus UCV San Juan de Lurigancho',4,42),
('UCV-CEN-CAL-01','Campo de Marte',1,0),('UCV-CEN-CAL-01','Plaza Bolognesi Breña',2,10),('UCV-CEN-CAL-01','Mercado Las Flores Breña',3,20),('UCV-CEN-CAL-01','Campus UCV Lima Centro',4,34),
('UTEC-BAR-MIR-01','Angamos / República de Panamá',1,0),('UTEC-BAR-MIR-01','Mercado N.º 1 de Surquillo',2,8),('UTEC-BAR-MIR-01','Larcomar',3,18),('UTEC-BAR-MIR-01','Campus UTEC Barranco',4,26),
('PUCP-NOR-01','Plaza Norte',1,0),('PUCP-NOR-01','Av. Túpac Amaru / Av. Honorio Delgado',2,18),('PUCP-NOR-01','Av. Perú / Dueñas',3,30),('PUCP-NOR-01','Campus PUCP San Miguel',4,55),
('PUCP-MAG-01','Parque Túpac Amaru',1,0),('PUCP-MAG-01','Mercado de Magdalena',2,6),('PUCP-MAG-01','Plaza San Miguel',3,16),('PUCP-MAG-01','Campus PUCP San Miguel',4,25),
('UNALM-EST-01','Mall Aventura Santa Anita',1,0),('UNALM-EST-01','Óvalo Santa Anita',2,12),('UNALM-EST-01','Molicentro',3,30),('UNALM-EST-01','Campus UNALM La Molina',4,48);

-- Completar/reconciliar los mappings. Primero liberar todos los órdenes de las
-- rutas administradas; así una corrección puede sustituir un stop sin chocar con
-- uq_route_stop_order. Después se restauran exactamente los órdenes 1..4.
UPDATE route_stops rs SET stop_order = rs.stop_order + 1000
FROM routes r
JOIN wp_lima_routes managed ON managed.code = r.code
WHERE rs.route_id = r.id
  AND rs.stop_order < 1000;

INSERT INTO route_stops (route_id, stop_id, stop_order,
                         estimated_minutes_from_start, active)
SELECT r.id, s.id, x.stop_order, x.minutes, TRUE
FROM wp_lima_route_stops x
JOIN routes r ON r.code = x.route_code
JOIN stops s ON s.name = x.stop_name
ON CONFLICT (route_id, stop_id) DO UPDATE
SET stop_order = EXCLUDED.stop_order,
    estimated_minutes_from_start = EXCLUDED.estimated_minutes_from_start,
    active = TRUE;

-- Si una matrícula usaba un stop retirado por esta auditoría, conservar la
-- matrícula y moverla al primer punto válido de su misma ruta antes de borrar el
-- mapping obsoleto. En el dataset actual no se requiere actualizar ninguna fila.
UPDATE student_route_enrollments e
SET stop_id = chosen.stop_id,
    updated_at = CURRENT_TIMESTAMP
FROM routes r
JOIN LATERAL (
    SELECT s.id AS stop_id
    FROM wp_lima_route_stops wanted
    JOIN stops s ON s.name = wanted.stop_name
    WHERE wanted.route_code = r.code
    ORDER BY wanted.stop_order
    LIMIT 1
) chosen ON TRUE
WHERE e.route_id = r.id
  AND EXISTS (SELECT 1 FROM wp_lima_routes managed WHERE managed.code = r.code)
  AND NOT EXISTS (
      SELECT 1
      FROM wp_lima_route_stops wanted
      JOIN stops s ON s.name = wanted.stop_name
      WHERE wanted.route_code = r.code AND s.id = e.stop_id
  );

DELETE FROM route_stops rs
USING routes r
WHERE rs.route_id = r.id
  AND EXISTS (SELECT 1 FROM wp_lima_routes managed WHERE managed.code = r.code)
  AND NOT EXISTS (
      SELECT 1
      FROM wp_lima_route_stops wanted
      JOIN stops s ON s.name = wanted.stop_name
      WHERE wanted.route_code = r.code AND s.id = rs.stop_id
  );

-- ---------------------------------------------------------------------------
-- 5. Horarios: lunes a viernes, llegadas 07/09/11/13/15/17/19/21.
-- ---------------------------------------------------------------------------
-- Retirar únicamente el horario demo no referenciado de R-003.
DELETE FROM route_schedules rs
USING routes r
WHERE rs.route_id = r.id AND r.code = 'UNI-EST-01'
  AND rs.day_of_week = 'MONDAY' AND rs.departure_time = TIME '06:50'
  AND NOT EXISTS (SELECT 1 FROM trips t WHERE t.route_schedule_id = rs.id);

-- Ajustar las cinco filas históricas referenciadas por trips a horarios del
-- catálogo nuevo, conservando sus IDs y por tanto todas las FK.
UPDATE route_schedules rs SET departure_time = TIME '06:15', estimated_arrival_time = TIME '07:00', active = TRUE
FROM routes r WHERE rs.route_id = r.id AND r.code = 'UPC-MON-SMI-01' AND rs.day_of_week = 'MONDAY' AND rs.departure_time = TIME '06:30';
UPDATE route_schedules rs SET departure_time = TIME '12:15', estimated_arrival_time = TIME '13:00', active = TRUE
FROM routes r WHERE rs.route_id = r.id AND r.code = 'UPC-MON-SMI-01' AND rs.day_of_week = 'MONDAY' AND rs.departure_time = TIME '13:00';
UPDATE route_schedules rs SET departure_time = TIME '06:15', estimated_arrival_time = TIME '07:00', active = TRUE
FROM routes r WHERE rs.route_id = r.id AND r.code = 'UPC-MON-SMI-01' AND rs.day_of_week = 'TUESDAY' AND rs.departure_time = TIME '07:00';
UPDATE route_schedules rs SET departure_time = TIME '06:05', estimated_arrival_time = TIME '07:00', active = TRUE
FROM routes r WHERE rs.route_id = r.id AND r.code = 'PUCP-NOR-01' AND rs.day_of_week = 'MONDAY' AND rs.departure_time = TIME '06:45';
UPDATE route_schedules rs SET departure_time = TIME '12:05', estimated_arrival_time = TIME '13:00', active = TRUE
FROM routes r WHERE rs.route_id = r.id AND r.code = 'PUCP-NOR-01' AND rs.day_of_week = 'MONDAY' AND rs.departure_time = TIME '13:15';

INSERT INTO route_schedules (route_id, day_of_week, departure_time,
                             estimated_arrival_time, active)
SELECT r.id, d.day_name,
       (a.arrival_time - make_interval(mins => x.duration_minutes))::TIME,
       a.arrival_time, TRUE
FROM wp_lima_routes x
JOIN routes r ON r.code = x.code
CROSS JOIN (VALUES ('MONDAY'),('TUESDAY'),('WEDNESDAY'),('THURSDAY'),('FRIDAY')) d(day_name)
CROSS JOIN (VALUES (TIME '07:00'),(TIME '09:00'),(TIME '11:00'),(TIME '13:00'),
                   (TIME '15:00'),(TIME '17:00'),(TIME '19:00'),(TIME '21:00')) a(arrival_time)
ON CONFLICT (route_id, day_of_week, departure_time) DO UPDATE
SET estimated_arrival_time = EXCLUDED.estimated_arrival_time,
    active = TRUE;

-- D4 debe seguir operativo y su unidad no puede anunciarse como disponible.
UPDATE vehicles v SET operational_status = 'IN_SERVICE', updated_at = CURRENT_TIMESTAMP
WHERE v.id = (SELECT t.vehicle_id FROM trips t WHERE t.id = 4 AND t.status = 'BOARDING');

-- ON CONFLICT consume valores de BIGSERIAL aun cuando termina en UPDATE. Como el
-- seed es transaccional, se resincroniza cada secuencia al máximo persistido para
-- que las reejecuciones no produzcan crecimiento artificial.
SELECT setval(pg_get_serial_sequence('institutions','id'), (SELECT COALESCE(MAX(id),1) FROM institutions), TRUE);
SELECT setval(pg_get_serial_sequence('stops','id'), (SELECT COALESCE(MAX(id),1) FROM stops), TRUE);
SELECT setval(pg_get_serial_sequence('routes','id'), (SELECT COALESCE(MAX(id),1) FROM routes), TRUE);
SELECT setval(pg_get_serial_sequence('route_stops','id'), (SELECT COALESCE(MAX(id),1) FROM route_stops), TRUE);
SELECT setval(pg_get_serial_sequence('route_schedules','id'), (SELECT COALESCE(MAX(id),1) FROM route_schedules), TRUE);

COMMIT;
