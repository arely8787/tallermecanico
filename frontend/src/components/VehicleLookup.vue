<script setup lang="ts">
/** Pantalla de solo lectura para localizar un vehículo vigente por VIN o placa. */
import { ref } from 'vue'
import Swal from 'sweetalert2'
import { lookupVehicle, type AuthenticatedApi, type VehicleLookupCriterion, type VehicleLookupResponse } from '../vehicleLookup'

interface Props {
  /** Cliente autenticado provisto por la pantalla principal. */
  api: AuthenticatedApi
}

const props = defineProps<Props>()
/** Solicita regresar al listado de Administración de vehículos sin modificar datos. */
const emit = defineEmits<{ back: [] }>()
const criterion = ref<VehicleLookupCriterion>('vin')
const value = ref('')
const result = ref<VehicleLookupResponse | null>(null)
const loading = ref(false)

/** Ejecuta la consulta de solo lectura y conserva la respuesta normal de ausencia. */
async function search(): Promise<void> {
  const query = value.value.trim()
  if (!query) {
    await Swal.fire({ icon: 'info', title: 'Escribe un dato para consultar', text: `Indica ${criterion.value === 'vin' ? 'el VIN' : 'la placa'} del vehículo.`, confirmButtonColor: '#7e22ce' })
    return
  }
  loading.value = true
  result.value = null
  try {
    result.value = await lookupVehicle(props.api, criterion.value, query)
  } catch (error) {
    await Swal.fire({ icon: 'error', title: 'No se pudo consultar el vehículo', text: error instanceof Error ? error.message : 'Ocurrió un error al consultar.', confirmButtonColor: '#7e22ce' })
  } finally {
    loading.value = false
  }
}

/** Limpia una respuesta anterior al cambiar el criterio de búsqueda. */
function changeCriterion(): void {
  value.value = ''
  result.value = null
}
</script>

<template>
  <section class="space-y-5">
    <div class="flex flex-wrap items-end justify-between gap-3">
      <div>
        <p class="text-sm font-semibold text-lilac-700">Consulta de vehículo</p>
        <h3 class="text-xl font-bold text-slate-900">Buscar por VIN o placa</h3>
      </div>
      <button class="text-sm font-semibold text-lilac-700" @click="emit('back')">Volver</button>
    </div>

    <form class="flex flex-wrap items-end gap-3 rounded-2xl border border-lilac-100 bg-white p-4 shadow-sm" @submit.prevent="search">
      <label class="min-w-40 text-sm font-medium">Buscar por
        <select v-model="criterion" @change="changeCriterion">
          <option value="vin">VIN</option>
          <option value="placa">Placa</option>
        </select>
      </label>
      <label class="min-w-60 flex-1 text-sm font-medium">{{ criterion === 'vin' ? 'VIN' : 'Placa' }}
        <input v-model="value" :placeholder="criterion === 'vin' ? '17 caracteres del VIN' : 'Ej. ABC1234'" autocomplete="off">
      </label>
      <button :disabled="loading" class="rounded-xl bg-lilac-700 px-4 py-3 text-sm font-semibold text-white disabled:bg-lilac-300">{{ loading ? 'Consultando…' : 'Consultar' }}</button>
    </form>

    <div v-if="result && !result.found" class="rounded-2xl border border-dashed border-lilac-300 bg-white p-8 text-center text-slate-600">No encontrado.</div>

    <article v-else-if="result?.vehicle" class="overflow-hidden rounded-2xl border border-lilac-100 bg-white shadow-sm">
      <header class="border-b border-lilac-100 bg-lilac-50 px-5 py-4">
        <p class="text-sm font-semibold text-lilac-700">Vehículo #{{ result.vehicle.id }}</p>
        <h4 class="text-lg font-bold text-slate-900">{{ result.vehicle.marca }} {{ result.vehicle.modelo }} · {{ result.vehicle.anio }}</h4>
      </header>
      <div class="grid gap-5 p-5 sm:grid-cols-2">
        <div class="space-y-2 text-sm"><p class="font-semibold text-slate-800">Datos del vehículo</p><p><span class="text-slate-500">VIN:</span> <span class="font-mono text-xs">{{ result.vehicle.vin }}</span></p><p><span class="text-slate-500">Placa:</span> {{ result.vehicle.placa }}</p><p><span class="text-slate-500">Serie:</span> {{ result.vehicle.numeroSerie }}</p><p><span class="text-slate-500">Versión:</span> {{ result.vehicle.version }}</p><p><span class="text-slate-500">Color:</span> {{ result.vehicle.color }}</p><p><span class="text-slate-500">Kilometraje:</span> {{ result.vehicle.kilometraje ?? 'No registrado' }}</p><p><span class="text-slate-500">Estatus:</span> {{ result.vehicle.status }}</p></div>
        <div class="space-y-2 text-sm"><p class="font-semibold text-slate-800">Propietario actual</p><p><span class="text-slate-500">Cliente:</span> {{ result.vehicle.cliente }}</p><p><span class="text-slate-500">ID cliente:</span> {{ result.vehicle.clienteId }}</p><p class="pt-3 font-semibold text-slate-800">Taller asociado</p><p><span class="text-slate-500">Taller:</span> {{ result.vehicle.taller || 'Sin taller asociado' }}</p><p v-if="result.vehicle.tallerId"><span class="text-slate-500">ID taller:</span> {{ result.vehicle.tallerId }}</p></div>
      </div>
    </article>
  </section>
</template>
