<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import Swal from 'sweetalert2'

const props = defineProps({ api: { type: Function, required: true }, client: { type: Object, default: null }, initialMode: { type: String, default: 'list' } })
const emit = defineEmits(['back', 'saved', 'lookup'])
const currentYear = new Date().getFullYear()
const form = reactive({ anio: '', marca: '', modelo: '', vin: '', numeroSerie: '', placa: '', version: '', color: '', kilometraje: '' })
const clients = ref([]), selectedClientId = ref(props.client?.id ? String(props.client.id) : '')
const brands = ref([]), models = ref([]), versions = ref([]), vehicles = ref([])
const clientVehicles = ref([])
const loading = ref(false), listLoading = ref(false), brandLoading = ref(false), modelLoading = ref(false), vinLoading = ref(false)
const mode = ref(props.client ? props.initialMode : 'list'), search = ref(''), workshopId = ref(''), yearTouched = ref(false)
const chosenModel = ref(null), selectedBrandInfo = ref(null), editingId = ref(null), lockedClient = ref(null)
const brandCache = new Map(), modelCache = new Map(), versionCache = new Map()
let brandTimer, modelTimer, searchTimer, keepModelOnBrandChange = false
const colorOptions = ['Blanco', 'Negro', 'Gris', 'Plata', 'Rojo', 'Azul', 'Verde', 'Amarillo', 'Naranja', 'Café', 'Beige', 'Vino', 'Dorado', 'Morado', 'Otro']

const currentClient = computed(() => props.client || lockedClient.value || clients.value.find(client => String(client.id) === selectedClientId.value) || null)
const selectedBrand = computed(() => selectedBrandInfo.value || brands.value.find(item => item.nombre.toLowerCase() === form.marca.trim().toLowerCase()))
const filteredBrands = computed(() => {
  const query = form.marca.trim().toLocaleLowerCase()
  return brands.value.filter(brand => !query || brand.nombre.toLocaleLowerCase().includes(query)).slice(0, 50)
})
const validYear = computed(() => /^\d{4}$/.test(String(form.anio)) && Number(form.anio) >= 1980 && Number(form.anio) <= currentYear + 1)
const yearError = computed(() => yearTouched.value && !validYear.value ? `Ingresa un año de 4 dígitos entre 1980 y ${currentYear + 1}.` : '')
const yearOptions = computed(() => {
  const knownYears = chosenModel.value?.anios || []
  const allYears = Array.from({ length: currentYear + 1 - 1980 + 1 }, (_, index) => currentYear + 1 - index)
  return [...new Set([...knownYears, ...allYears])]
})
const workshopOptions = computed(() => {
  const entries = new Map()
  vehicles.value.forEach(vehicle => { if (vehicle.tallerId && vehicle.taller) entries.set(vehicle.tallerId, vehicle.taller) })
  return [...entries].map(([id, name]) => ({ id, name })).sort((a, b) => a.name.localeCompare(b.name))
})

async function showError(error, title = 'No se pudo registrar el vehículo') { await Swal.fire({ icon: 'error', title, text: error.message, confirmButtonColor: '#7e22ce' }) }
async function loadActiveClients() { if (!props.client) try { clients.value = await props.api('/api/clientes/activos', null, 'GET') } catch (error) { await showError(error, 'No se pudieron cargar los clientes') } }
async function loadVehicles() {
  listLoading.value = true
  try {
    const params = new URLSearchParams()
    if (search.value.trim()) params.set('q', search.value.trim())
    if (workshopId.value) params.set('tallerId', workshopId.value)
    vehicles.value = await props.api(`/api/vehiculos${params.size ? `?${params}` : ''}`, null, 'GET')
  } catch (error) { await showError(error, 'No se pudieron cargar los vehículos') } finally { listLoading.value = false }
}
async function loadClientVehicles() {
  if (!props.client?.id) return
  listLoading.value = true
  try { clientVehicles.value = await props.api(`/api/vehiculos/cliente/${props.client.id}`, null, 'GET') }
  catch (error) { await showError(error, 'No se pudieron consultar los vehículos del cliente') }
  finally { listLoading.value = false }
}
async function loadBrands(refresh = false) {
  const query = form.marca.trim()
  if (query && query.length < 3) return
  const key = query.toLowerCase()
  if (brandCache.has(key) && !refresh) { brands.value = brandCache.get(key); return }
  brandLoading.value = true
  try {
    const params = new URLSearchParams()
    if (query.length >= 3) params.set('q', query)
    if (refresh) params.set('refresh', 'true')
    const result = await props.api(`/api/vehiculos/marcas${params.size ? `?${params}` : ''}`, null, 'GET')
    brandCache.set(key, result); brands.value = result
  } catch (_) { /* Se conserva la caché disponible mientras vPIC no responde. */ } finally { brandLoading.value = false }
}
async function refreshBrandCache() {
  try {
    const result = await props.api('/api/vehiculos/marcas?refresh=true', null, 'GET')
    brandCache.set('', result)
    if (!form.marca.trim()) brands.value = result
  } catch (_) { /* La caché local sigue disponible si vPIC no responde. */ }
}
async function loadModels() {
  if (!selectedBrand.value || !validYear.value) return loadCachedModels()
  const query = form.modelo.trim()
  if (query && query.length < 3) return
  const key = `${selectedBrand.value.id}:${form.anio}:${query.toLowerCase()}`
  if (modelCache.has(key)) { models.value = modelCache.get(key); return }
  modelLoading.value = true
  try {
    const params = new URLSearchParams({ anio: String(form.anio) })
    if (query.length >= 3) params.set('q', query)
    const result = await props.api(`/api/vehiculos/marcas/${selectedBrand.value.id}/modelos?${params}`, null, 'GET')
    modelCache.set(key, result); models.value = result
  } catch (_) { /* Se conserva la caché disponible mientras vPIC no responde. */ } finally { modelLoading.value = false }
}
async function loadCachedModels() {
  const query = form.modelo.trim()
  if (query && query.length < 3) return
  const key = `cached:${query.toLowerCase()}`
  if (modelCache.has(key)) { models.value = modelCache.get(key); return }
  modelLoading.value = true
  try {
    const result = await props.api(`/api/vehiculos/modelos${query.length >= 3 ? `?q=${encodeURIComponent(query)}` : ''}`, null, 'GET')
    modelCache.set(key, result); models.value = result
  } catch (_) { /* No se interrumpe la escritura si el catálogo no puede consultarse. */ } finally { modelLoading.value = false }
}
async function loadVersions() {
  if (!chosenModel.value?.id) { versions.value = []; return }
  const query = form.version.trim().toLowerCase()
  const key = `${chosenModel.value.id}:${query}`
  if (versionCache.has(key)) { versions.value = versionCache.get(key); return }
  try {
    const result = await props.api(`/api/vehiculos/modelos/${chosenModel.value.id}/versiones${query ? `?q=${encodeURIComponent(query)}` : ''}`, null, 'GET')
    versionCache.set(key, result); versions.value = result
  } catch (_) { versions.value = [] }
}
function debounceBrandSearch() {
  clearTimeout(brandTimer)
  if (!form.marca.trim() || form.marca.trim().length >= 3) { brandLoading.value = true; brandTimer = setTimeout(() => loadBrands(), 300) }
}
function debounceModelSearch() {
  clearTimeout(modelTimer)
  if (!form.modelo.trim() || form.modelo.trim().length >= 3) { modelLoading.value = true; modelTimer = setTimeout(() => loadModels(), 300) }
}
function onYearBlur() { yearTouched.value = true; if (validYear.value) loadModels() }
function selectBrand() {
  selectedBrandInfo.value = brands.value.find(brand => brand.nombre.toLowerCase() === form.marca.trim().toLowerCase()) || null
  if (selectedBrand.value && validYear.value) loadModels()
}
function selectModel() {
  const normalized = form.modelo.trim().toLowerCase()
  const matches = models.value.filter(model => model.nombre.trim().toLowerCase() === normalized)
  const model = matches.length === 1 ? matches[0] : null
  chosenModel.value = model
  void loadVersions()
  if (!model || form.marca.trim()) return
  const sameNameBrands = new Set(matches.map(item => item.marcaId))
  if (sameNameBrands.size !== 1) return
  if (!brands.value.some(item => item.id === model.marcaId)) brands.value = [...brands.value, { id: model.marcaId, nombre: model.marca }]
  selectedBrandInfo.value = { id: model.marcaId, nombre: model.marca }
  keepModelOnBrandChange = true
  form.marca = model.marca
}
function onBrandInput() { selectedBrandInfo.value = null; debounceBrandSearch() }
function onBrandFocus() { void loadBrands() }
function onModelInput() { chosenModel.value = null; versions.value = []; debounceModelSearch() }
function onVersionInput() { loadVersions() }
async function decodeVin() {
  const vin = form.vin.trim().toUpperCase()
  if (!/^[A-HJ-NPR-Z0-9]{17}$/.test(vin)) return
  vinLoading.value = true
  try {
    const decoded = await props.api(`/api/vehiculos/decodificar-vin/${encodeURIComponent(vin)}`, null, 'GET')
    if (!form.anio && decoded?.anio) form.anio = String(decoded.anio)
    if (!form.marca && decoded?.marca) form.marca = decoded.marca
    if (!form.modelo && decoded?.modelo) form.modelo = decoded.modelo
    if (!form.version && decoded?.version) form.version = decoded.version
  } catch (_) { /* La decodificación es opcional y no interrumpe la captura. */ } finally { vinLoading.value = false }
}
function onSearchInput() { clearTimeout(searchTimer); searchTimer = setTimeout(loadVehicles, 300) }
function resetForm() {
  Object.assign(form, { anio: '', marca: '', modelo: '', vin: '', numeroSerie: '', placa: '', version: '', color: '', kilometraje: '' })
  selectedClientId.value = props.client?.id ? String(props.client.id) : ''
  models.value = []; versions.value = []; chosenModel.value = null; selectedBrandInfo.value = null; lockedClient.value = null; editingId.value = null; yearTouched.value = false
}
function openForm() { resetForm(); mode.value = 'form'; loadActiveClients() }
function cancelForm() { if (props.client) { resetForm(); mode.value = props.initialMode === 'client' ? 'client' : 'form'; if (mode.value === 'client') loadClientVehicles() } else { resetForm(); mode.value = 'list' } }
async function openEdit(vehicle) {
  try {
    const detail = await props.api(`/api/vehiculos/${vehicle.id}`, null, 'GET')
    editingId.value = detail.id
    lockedClient.value = { id: detail.clienteId, fullName: detail.cliente }
    keepModelOnBrandChange = true
    Object.assign(form, { anio: String(detail.anio), marca: detail.marca, modelo: detail.modelo, vin: detail.vin, numeroSerie: detail.numeroSerie, placa: detail.placa, version: detail.version, color: detail.color, kilometraje: detail.kilometraje ?? '' })
    if (!brands.value.some(brand => brand.id === detail.marcaId)) brands.value = [...brands.value, { id: detail.marcaId, nombre: detail.marca }]
    selectedBrandInfo.value = { id: detail.marcaId, nombre: detail.marca }
    chosenModel.value = { id: detail.modeloId, nombre: detail.modelo, marcaId: detail.marcaId, marca: detail.marca, anios: [detail.anio] }
    models.value = [chosenModel.value]; await loadVersions(); mode.value = 'form'
  } catch (error) { await showError(error, 'No se pudo cargar el vehículo') }
}
async function deleteVehicle(vehicle) {
  const result = await Swal.fire({ icon: 'warning', title: '¿Eliminar vehículo?', text: 'El vehículo se conservará con estatus ELIMINADO y dejará de aparecer en esta lista.', showCancelButton: true, confirmButtonText: 'Eliminar', cancelButtonText: 'Cancelar', confirmButtonColor: '#be123c' })
  if (!result.isConfirmed) return
  try {
    await props.api(`/api/vehiculos/${vehicle.id}/eliminar`, null, 'PATCH')
    await Swal.fire({ icon: 'success', title: 'Vehículo eliminado', confirmButtonColor: '#7e22ce' })
    await loadVehicles()
  } catch (error) { await showError(error, 'No se pudo eliminar el vehículo') }
}
/**
 * Transfiere el vehículo al cliente activo elegido y conserva la vista actual.
 * Desde la lista administrativa consulta el detalle existente para excluir al propietario vigente del selector.
 */
async function transferVehicle(vehicle) {
  let currentOwnerId = props.client?.id
  if (!currentOwnerId) {
    try { currentOwnerId = (await props.api(`/api/vehiculos/${vehicle.id}`, null, 'GET')).clienteId }
    catch (error) { await showError(error, 'No se pudo obtener el propietario actual'); return }
  }
  await loadActiveClients()
  const destinations = clients.value.filter(client => client.id !== currentOwnerId)
  if (!destinations.length) { await Swal.fire({ icon: 'info', title: 'No hay cliente destino', text: 'No hay otro cliente ACTIVO disponible para la transferencia.', confirmButtonColor: '#7e22ce' }); return }
  const options = Object.fromEntries(destinations.map(client => [String(client.id), `${client.fullName} · ${client.email}`]))
  const selection = await Swal.fire({
    title: 'Cambiar propietario', text: 'El vehículo dejará de pertenecer al cliente actual y se conservará el historial.',
    input: 'select', inputOptions: options, inputPlaceholder: 'Selecciona un cliente ACTIVO', showCancelButton: true,
    confirmButtonText: 'Transferir', cancelButtonText: 'Cancelar', confirmButtonColor: '#7e22ce',
    inputValidator: value => value ? undefined : 'Selecciona el cliente destino'
  })
  if (!selection.isConfirmed) return
  try {
    const result = await props.api(`/api/vehiculos/${vehicle.id}/transferir`, { clienteDestinoId: Number(selection.value) }, 'PATCH')
    await Swal.fire({ icon: 'success', title: result.transferred ? 'Vehículo transferido' : 'Sin cambios', text: result.transferred ? `Ahora pertenece a ${result.cliente}.` : 'El vehículo ya pertenece al cliente destino.', confirmButtonColor: '#7e22ce' })
    if (props.client) await loadClientVehicles()
    else await loadVehicles()
  } catch (error) { await showError(error, 'No se pudo transferir el vehículo') }
}
async function save() {
  yearTouched.value = true
  if (!validYear.value) { await Swal.fire({ icon: 'error', title: 'Año no válido', text: `Ingresa un año de 4 dígitos entre 1980 y ${currentYear + 1}.`, confirmButtonColor: '#7e22ce' }); return }
  if (!currentClient.value) { await Swal.fire({ icon: 'error', title: 'Cliente requerido', text: 'Selecciona un cliente ACTIVO para registrar el vehículo.', confirmButtonColor: '#7e22ce' }); return }
  loading.value = true
  try {
    const isEdit = Boolean(editingId.value)
    await props.api(isEdit ? `/api/vehiculos/${editingId.value}` : '/api/vehiculos', { ...form, anio: Number(form.anio), kilometraje: form.kilometraje === '' ? null : Number(form.kilometraje), clienteId: currentClient.value.id }, isEdit ? 'PUT' : 'POST')
    await Swal.fire({ icon: 'success', title: isEdit ? 'Vehículo actualizado' : 'Vehículo registrado', confirmButtonColor: '#7e22ce' })
    resetForm(); mode.value = props.client ? (props.initialMode === 'client' ? 'client' : 'form') : 'list'
    if (mode.value === 'client') await loadClientVehicles(); else await loadVehicles()
    emit('saved')
  } catch (error) { await showError(error) } finally { loading.value = false }
}

watch(() => form.anio, () => { models.value = []; yearTouched.value = false; if (validYear.value && selectedBrand.value) loadModels() })
watch(() => form.marca, () => {
  models.value = []
  versions.value = []
  if (selectedBrandInfo.value?.nombre.toLowerCase() !== form.marca.trim().toLowerCase()) selectedBrandInfo.value = null
  if (keepModelOnBrandChange) keepModelOnBrandChange = false
  else { form.modelo = ''; chosenModel.value = null }
  if (selectedBrand.value && validYear.value) loadModels()
})
onMounted(async () => {
  await loadBrands()
  if (mode.value === 'list') await loadVehicles()
  else if (mode.value === 'client') await loadClientVehicles()
  else await loadActiveClients()
  setTimeout(refreshBrandCache, 300)
})
</script>

<template>
  <section v-if="mode === 'list'" class="space-y-5">
    <div class="flex flex-wrap items-end justify-between gap-3"><div class="flex flex-1 flex-wrap gap-3"><label class="min-w-52 flex-1 text-sm font-medium">Buscar<input v-model="search" placeholder="Placa, VIN o cliente" @input="onSearchInput"></label><label class="min-w-48 text-sm font-medium">Taller<select v-model="workshopId" @change="loadVehicles"><option value="">Todos los talleres</option><option v-for="workshop in workshopOptions" :key="workshop.id" :value="String(workshop.id)">{{ workshop.name }}</option></select></label></div><div class="flex gap-3"><button class="rounded-xl border border-lilac-200 px-4 py-3 text-sm font-semibold text-lilac-700" @click="emit('lookup')">Consultar vehículo</button><button class="rounded-xl bg-lilac-700 px-4 py-3 text-sm font-semibold text-white" @click="openForm">Registrar vehículo</button></div></div>
    <div class="overflow-x-auto rounded-2xl border border-lilac-100 bg-white shadow-sm"><table class="min-w-full text-left text-sm"><thead class="bg-lilac-50 text-slate-600"><tr><th class="px-4 py-3 font-semibold">Cliente</th><th class="px-4 py-3 font-semibold">Taller</th><th class="px-4 py-3 font-semibold">Marca</th><th class="px-4 py-3 font-semibold">Modelo</th><th class="px-4 py-3 font-semibold">Año</th><th class="px-4 py-3 font-semibold">Placa</th><th class="px-4 py-3 font-semibold">VIN</th><th class="px-4 py-3 font-semibold">Status</th><th class="px-4 py-3 font-semibold">Acciones</th></tr></thead><tbody><tr v-if="listLoading"><td colspan="9" class="px-4 py-8 text-center text-slate-500">Cargando vehículos…</td></tr><tr v-else-if="!vehicles.length"><td colspan="9" class="px-4 py-8 text-center text-slate-500">No hay vehículos registrados.</td></tr><tr v-for="vehicle in vehicles" :key="vehicle.id" class="border-t border-lilac-100"><td class="px-4 py-3">{{ vehicle.cliente }}</td><td class="px-4 py-3">{{ vehicle.taller || 'Sin taller' }}</td><td class="px-4 py-3">{{ vehicle.marca }}</td><td class="px-4 py-3">{{ vehicle.modelo }}</td><td class="px-4 py-3">{{ vehicle.anio }}</td><td class="px-4 py-3">{{ vehicle.placa }}</td><td class="px-4 py-3 font-mono text-xs">{{ vehicle.vin }}</td><td class="px-4 py-3">{{ vehicle.status }}</td><td class="px-4 py-3"><div class="flex gap-3"><button class="font-semibold text-lilac-700" @click="openEdit(vehicle)">Editar</button><button class="font-semibold text-lilac-700" @click="transferVehicle(vehicle)">Transferir</button><button class="font-semibold text-rose-700" @click="deleteVehicle(vehicle)">Eliminar</button></div></td></tr></tbody></table></div>
  </section>
  <section v-else-if="mode === 'client'" class="space-y-5">
    <div class="flex flex-wrap items-center justify-between gap-3"><div><p class="text-sm font-semibold text-lilac-700">Vehículos actuales</p><h3 class="text-xl font-bold">{{ client?.fullName }}</h3></div><div class="flex gap-3"><button class="rounded-xl bg-lilac-700 px-4 py-3 text-sm font-semibold text-white" @click="openForm">Asociar vehículo</button><button class="text-sm font-semibold text-lilac-700" @click="emit('back')">Volver</button></div></div>
    <div class="overflow-x-auto rounded-2xl border border-lilac-100 bg-white shadow-sm"><table class="min-w-full text-left text-sm"><thead class="bg-lilac-50 text-slate-600"><tr><th class="px-4 py-3 font-semibold">Marca</th><th class="px-4 py-3 font-semibold">Modelo</th><th class="px-4 py-3 font-semibold">Año</th><th class="px-4 py-3 font-semibold">Placa</th><th class="px-4 py-3 font-semibold">VIN</th><th class="px-4 py-3 font-semibold">Acciones</th></tr></thead><tbody><tr v-if="listLoading"><td colspan="6" class="px-4 py-8 text-center text-slate-500">Consultando vehículos…</td></tr><tr v-else-if="!clientVehicles.length"><td colspan="6" class="px-4 py-8 text-center text-slate-500">Este cliente no tiene vehículos actuales.</td></tr><tr v-for="vehicle in clientVehicles" :key="vehicle.id" class="border-t border-lilac-100"><td class="px-4 py-3">{{ vehicle.marca }}</td><td class="px-4 py-3">{{ vehicle.modelo }}</td><td class="px-4 py-3">{{ vehicle.anio }}</td><td class="px-4 py-3">{{ vehicle.placa }}</td><td class="px-4 py-3 font-mono text-xs">{{ vehicle.vin }}</td><td class="px-4 py-3"><button class="font-semibold text-lilac-700" @click="transferVehicle(vehicle)">Cambiar propietario</button></td></tr></tbody></table></div>
  </section>
  <section v-else class="space-y-5">
    <div v-if="currentClient && (client || lockedClient)" class="rounded-2xl border border-lilac-100 bg-white p-4 text-sm shadow-sm"><p class="font-semibold">Cliente</p><p class="text-slate-600">{{ currentClient.fullName }}<span v-if="currentClient.email"> · {{ currentClient.email }}</span></p></div>
    <form class="space-y-4" @submit.prevent="save">
      <label v-if="!client && !lockedClient" class="block text-sm font-medium">Cliente<select v-model="selectedClientId" required><option value="" disabled>Selecciona un cliente ACTIVO</option><option v-for="availableClient in clients" :key="availableClient.id" :value="String(availableClient.id)">{{ availableClient.fullName }} · {{ availableClient.email }}</option></select></label>
      <div class="grid gap-4 sm:grid-cols-2">
        <label class="block text-sm font-medium">Año<input v-model="form.anio" list="vehicle-years" type="text" inputmode="numeric" maxlength="4" required @blur="onYearBlur"><datalist id="vehicle-years"><option v-for="year in yearOptions" :key="year" :value="year"/></datalist><span v-if="yearError" class="mt-1 block text-xs text-rose-600">{{ yearError }}</span></label>
        <label class="block text-sm font-medium">Marca<input v-model="form.marca" list="vehicle-brands" required maxlength="100" @focus="onBrandFocus" @input="onBrandInput" @change="selectBrand"><span v-if="brandLoading" class="mt-1 block text-xs text-slate-500">Cargando…</span><datalist id="vehicle-brands"><option v-for="brand in filteredBrands" :key="brand.id" :value="brand.nombre"/></datalist></label>
        <label class="block text-sm font-medium">Modelo<input v-model="form.modelo" list="vehicle-models" required maxlength="120" @focus="loadModels" @input="onModelInput" @change="selectModel"><span v-if="modelLoading" class="mt-1 block text-xs text-slate-500">Cargando…</span><datalist id="vehicle-models"><option v-for="model in models" :key="`${model.id}-${model.marcaId}`" :value="model.nombre" :label="model.marca ? `${model.nombre} · ${model.marca}` : model.nombre"/></datalist></label>
        <label class="block text-sm font-medium">VIN<input v-model="form.vin" required minlength="17" maxlength="17" @input="form.vin = form.vin.toUpperCase()" @blur="decodeVin"><span v-if="vinLoading" class="mt-1 block text-xs text-slate-500">Consultando VIN…</span></label>
        <label class="block text-sm font-medium">Número de serie<input v-model="form.numeroSerie" required minlength="5" maxlength="20" @input="form.numeroSerie = form.numeroSerie.toUpperCase()"></label>
        <label class="block text-sm font-medium">Placa<input v-model="form.placa" required minlength="5" maxlength="10" @input="form.placa = form.placa.toUpperCase().replace(/[ -]/g, '')"></label>
        <label class="block text-sm font-medium">Versión<input v-model="form.version" list="vehicle-versions" required maxlength="100" @focus="loadVersions" @input="onVersionInput"><datalist id="vehicle-versions"><option v-for="version in versions" :key="version" :value="version"/></datalist></label>
        <label class="block text-sm font-medium">Color<input v-model="form.color" list="vehicle-colors" required maxlength="60"><datalist id="vehicle-colors"><option v-for="color in colorOptions" :key="color" :value="color"/></datalist></label>
        <label class="block text-sm font-medium">Kilometraje<input v-model="form.kilometraje" type="number" min="0"></label>
      </div>
      <button :disabled="loading" class="w-full rounded-xl bg-lilac-700 py-3 font-semibold text-white">{{ loading ? 'Guardando…' : editingId ? 'Guardar cambios' : 'Registrar vehículo' }}</button><button type="button" class="w-full text-sm font-semibold text-lilac-700" @click="cancelForm">Cancelar</button>
    </form>
  </section>
</template>
