<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import Swal from 'sweetalert2'

const props = defineProps({ api: { type: Function, required: true }, isAdmin: Boolean })
const emit = defineEmits(['back'])
const workshops = ref([]), workshopId = ref(''), search = ref('')
const clients = ref([]), page = ref(0), totalPages = ref(0), totalRecords = ref(0), loading = ref(false)
const modal = ref(''), selectedClient = ref(null)
const form = reactive({ status: '', motivoStatus: '' })
const statusOptions = () => props.isAdmin ? ['ACTIVO', 'SUSPENDIDO', 'ELIMINADO'] : ['ACTIVO', 'SUSPENDIDO']
const filteredClients = computed(() => {
  const query = search.value.trim().toLocaleLowerCase()
  return clients.value.filter(record => !query || record.fullName.toLocaleLowerCase().includes(query))
})

async function showError(error, title) { await Swal.fire({ icon: 'error', title, text: error.message, confirmButtonColor: '#7e22ce' }) }
async function loadWorkshops() { try { workshops.value = await props.api(props.isAdmin ? '/api/talleres' : '/api/talleres/activos', null, 'GET') } catch (error) { await showError(error, 'No se pudieron cargar los talleres') } }
async function load(target = 0) {
  if (!workshopId.value) { clients.value = []; page.value = 0; totalPages.value = 0; totalRecords.value = 0; return }
  loading.value = true
  try {
    const data = await props.api(`/api/administracion/clientes/estatus?tallerId=${encodeURIComponent(workshopId.value)}&page=${target}&size=10`, null, 'GET')
    clients.value = data.data; page.value = data.page; totalPages.value = data.totalPages; totalRecords.value = data.totalRecords
  } catch (error) { await showError(error, 'No se pudieron cargar los estatus de clientes') } finally { loading.value = false }
}
function edit(record) { selectedClient.value = record; form.status = record.status; form.motivoStatus = record.motivoStatus || ''; modal.value = 'edit' }
function closeModal() { modal.value = ''; selectedClient.value = null; form.status = ''; form.motivoStatus = '' }
async function save() {
  if (form.status === 'ELIMINADO') {
    const confirmation = await Swal.fire({ icon: 'warning', title: '¿Eliminar cliente?', text: 'El registro y su fotografía se conservarán con estatus ELIMINADO.', showCancelButton: true, confirmButtonText: 'Eliminar', cancelButtonText: 'Cancelar', confirmButtonColor: '#be123c' })
    if (!confirmation.isConfirmed) return
  }
  loading.value = true
  try {
    await props.api(`/api/administracion/clientes/${selectedClient.value.id}/estatus`, { ...form }, 'PATCH')
    await Swal.fire({ icon: 'success', title: 'Estatus actualizado', text: 'Los cambios se guardaron correctamente.', confirmButtonColor: '#7e22ce' })
    closeModal(); await load(page.value)
  } catch (error) { await showError(error, 'No se pudo actualizar el estatus') } finally { loading.value = false }
}
onMounted(loadWorkshops)
</script>

<template>
  <section class="space-y-4">
    <div class="flex flex-wrap items-end gap-3"><label class="min-w-52 flex-1 text-sm font-medium">Taller<select v-model="workshopId" @change="load(0)"><option value="" disabled>Selecciona un taller</option><option value="SIN_TALLER">Sin taller</option><option v-for="workshop in workshops" :key="workshop.id" :value="String(workshop.id)">{{ workshop.name }}</option></select></label><label class="min-w-52 flex-1 text-sm font-medium">Buscar cliente<input v-model="search" placeholder="Nombre del cliente"></label></div>
    <p class="text-sm text-slate-500">{{ totalRecords }} cliente(s) · página {{ page + 1 }} de {{ Math.max(1, totalPages) }}</p>
    <div v-if="!workshopId" class="rounded-xl border border-dashed border-lilac-300 p-8 text-center text-slate-500">Selecciona un taller para consultar el estatus de sus clientes.</div>
    <div v-else class="overflow-hidden rounded-2xl border border-lilac-100 bg-white shadow-sm"><div class="overflow-x-auto"><table class="w-full min-w-[760px] text-left text-sm"><thead class="bg-lilac-50 text-xs font-semibold uppercase tracking-wide text-lilac-700"><tr><th class="px-4 py-3">ID</th><th class="px-4 py-3">Valor</th><th class="px-4 py-3">Cliente</th><th class="px-4 py-3">Descripción</th><th class="px-4 py-3 text-right">Acciones</th></tr></thead><tbody class="divide-y divide-lilac-100"><tr v-for="record in filteredClients" :key="record.id"><td class="px-4 py-3 text-slate-500">{{ record.id }}</td><td class="px-4 py-3"><span :class="record.status === 'ACTIVO' ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'" class="rounded-full px-2.5 py-1 text-xs font-semibold">{{ record.status }}</span></td><td class="px-4 py-3 font-semibold">{{ record.fullName }}</td><td class="px-4 py-3 text-slate-600">{{ record.statusDescription || 'Sin descripción' }}</td><td class="px-4 py-3 text-right"><button class="font-semibold text-lilac-700" @click="edit(record)">Editar</button></td></tr><tr v-if="!filteredClients.length && !loading"><td colspan="5" class="px-4 py-8 text-center text-slate-500">No hay clientes para mostrar.</td></tr></tbody></table></div></div>
    <div class="flex justify-between"><button :disabled="page === 0" class="text-sm font-semibold text-lilac-700 disabled:text-slate-300" @click="load(page - 1)">Anterior</button><button :disabled="page + 1 >= totalPages" class="text-sm font-semibold text-lilac-700 disabled:text-slate-300" @click="load(page + 1)">Siguiente</button></div>
    <button class="text-sm font-semibold text-lilac-700" @click="emit('back')">Volver</button>

    <div v-if="modal === 'edit'" class="fixed inset-0 z-[60] flex items-center justify-center bg-slate-950/45 p-4" @click.self="closeModal"><form class="w-full max-w-lg space-y-5 rounded-2xl bg-white p-6 shadow-2xl" @submit.prevent="save"><div class="flex items-center justify-between"><h3 class="text-xl font-bold">Editar estatus</h3><button type="button" class="text-sm font-semibold text-lilac-700" @click="closeModal">Cerrar</button></div><label class="block text-sm font-medium">Estatus actual<input :value="selectedClient?.status" readonly class="bg-slate-50 text-slate-500"></label><label class="block text-sm font-medium">Nuevo estatus<select v-model="form.status" required><option v-for="status in statusOptions()" :key="status" :value="status">{{ status }}</option></select></label><label class="block text-sm font-medium">Descripción<textarea v-model="form.motivoStatus" maxlength="1000" rows="4" class="w-full rounded-xl border border-lilac-300 bg-white px-4 py-3 outline-none transition focus:border-lilac-500 focus:ring-4 focus:ring-lilac-100"></textarea></label><div class="flex gap-3"><button :disabled="loading" class="flex-1 rounded-xl bg-lilac-700 py-3 font-semibold text-white">Guardar cambios</button><button type="button" class="rounded-xl px-4 text-sm font-semibold text-lilac-700" @click="closeModal">Cancelar</button></div></form></div>
  </section>
</template>
