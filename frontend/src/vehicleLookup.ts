/** Cliente mínimo de la API autenticada que comparten las pantallas internas. */
export type AuthenticatedApi = (url: string, body?: unknown, method?: string) => Promise<unknown>

/** Identificadores exactos admitidos por la consulta de vehículos. */
export type VehicleLookupCriterion = 'vin' | 'placa'

/** Datos no sensibles necesarios para identificar el vehículo, su dueño vigente y su taller. */
export interface VehicleLookupVehicle {
  id: number
  vin: string
  numeroSerie: string
  placa: string
  marca: string
  modelo: string
  anio: number
  version: string
  color: string
  kilometraje: number | null
  clienteId: number
  cliente: string
  tallerId: number | null
  taller: string | null
  status: string
}

/** La ausencia se modela como resultado válido de la consulta, no como error HTTP. */
export interface VehicleLookupResponse {
  found: boolean
  vehicle: VehicleLookupVehicle | null
}

/**
 * Busca una coincidencia exacta por un único identificador de vehículo.
 * El token de sesión lo incorpora el cliente de API que recibe como parámetro.
 */
export function lookupVehicle(api: AuthenticatedApi, criterion: VehicleLookupCriterion, value: string): Promise<VehicleLookupResponse> {
  const parameters = new URLSearchParams({ [criterion]: value.trim() })
  return api(`/api/vehiculos/consulta?${parameters.toString()}`, null, 'GET') as Promise<VehicleLookupResponse>
}
