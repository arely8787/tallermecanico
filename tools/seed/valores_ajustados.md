# Valores ajustados para la carga semilla

Este registro no modifica el archivo Excel original. Los valores nuevos son ficticios y se usan solamente en `importar_datos.py` al enviar datos a los endpoints.

## Talleres

| Registro | Campo | Valor original | Valor nuevo |
|---|---|---|---|
| Taller Morado Norte | Razón social | No proporcionado | Operadora Talleres Boreal Ficticia, S.A. de C.V. |
| Taller Morado Norte | RFC | No proporcionado | OTB260115A1B |
| Taller Morado Norte | Teléfono personal | No proporcionado | 8001234511 |
| Taller Morado Norte | Foto | No proporcionada | `assets/taller-generico.jpg` |
| Taller Morado Sur | Razón social | No proporcionado | Mecánica Meridiana Simulada, S.A. de C.V. |
| Taller Morado Sur | RFC | No proporcionado | MMS260216C3D |
| Taller Morado Sur | Teléfono personal | No proporcionado | 8001234512 |
| Taller Morado Sur | Foto | No proporcionada | `assets/taller-generico.jpg` |
| Taller Morado Poniente | Razón social | No proporcionado | Servicio Poniente Imaginario, S.A. de C.V. |
| Taller Morado Poniente | RFC | No proporcionado | SPI260317E4F |
| Taller Morado Poniente | Teléfono personal | No proporcionado | 8001234513 |
| Taller Morado Poniente | Foto | No proporcionada | `assets/taller-generico.jpg` |
| Taller Morado Poniente | Colonia | Centro | Puebla Centro |

## Números de serie de vehículos

| Cliente | Marca / modelo | Campo | Valor original | Valor nuevo |
|---|---|---|---|---|
| 24, Omar Espinoza Treviño | Mazda CX-3 | Número de serie | Vacío | SEED260001 |
| 28, Iván Zúñiga Escobar | Kia Sportage | Número de serie | Vacío | SEED260002 |
| 32, Rodrigo Fierro Cisneros | Honda Fit | Número de serie | Vacío | SEED260003 |
| 35, Brenda Colín Sandoval | Hyundai Accent | Número de serie | Vacío | SEED260004 |
| 39, Rocío Vera Alcántara | Kia Soul | Número de serie | Vacío | SEED260007 |
| 42, Adrián Solano Heredia | Ford Explorer | Número de serie | Vacío | SEED260008 |

## Direcciones de clientes

Se conserva siempre la calle y el código postal. El estado de Alejandra se ajusta a la denominación exacta del catálogo para que la dirección sea válida.

| Cliente | Campo | Valor original | Valor nuevo |
|---|---|---|---|
| 26, Joaquín | Colonia | Centro | Hermosillo Centro |
| 27, Marisol | Colonia | Centro | Aguascalientes Centro |
| 30, Sergio | Colonia | Centro | Zona Central |
| 31, Alejandra | Estado | Coahuila | Coahuila de Zaragoza |
| 31, Alejandra | Colonia | Centro | Torreón Centro |
| 32, Rodrigo | Colonia | Centro | Guanajuato Centro |
| 34, Ernesto | Colonia | Centro | Tepic Centro |
| 35, Brenda | Colonia | Centro | Colima Centro |
| 36, Mauricio | Colonia | Centro | Tepatitlán de Morelos Centro |
| 37, Cecilia | Colonia | Centro | Zacatecas Centro |
| 38, Alan | Colonia | Centro | Tlaxcala Centro |
| 40, Tomás | Colonia | Centro | Nuevo Laredo Centro |
| 41, Fabiola | Colonia | Centro | Ciudad Victoria Centro |
| 42, Adrián | Colonia | Centro | Chetumal Centro |
| 45, Perla | Colonia | Centro | Puebla Centro |
| 46, Emilio | Colonia | Centro | Mérida Centro |
| 25, Ximena | Colonia / municipio | Guadalajara Centro / Guadalajara | Sin cambio: no existe ninguna coincidencia para el CP 45000 en el catálogo local; se omite la carga para no cambiar el CP. |
