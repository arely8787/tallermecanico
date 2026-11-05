<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import Swal from 'sweetalert2'
import StatusManagement from './components/StatusManagement.vue'
import VehicleRegistration from './components/VehicleRegistration.vue'
import VehicleLookup from './components/VehicleLookup.vue'
import ClientEditModal from './components/ClientEditModal.vue'

const screen = ref('login'), notice = ref(''), error = ref(''), devToken = ref(''), loading = ref(false)
const session = ref(null), employees = ref([]), workshops = ref([]), activeWorkshops = ref([]), states = ref([])
const editingClientId = ref(null), editingWorkshopId = ref(null), selectedPhoto = ref(null), photoPreview = ref(''), workshopPhoto = ref(null)
const clientEditOpen = ref(false), vehicleClient = ref(null), vehicleInitialMode = ref('list'), vehicleReturnScreen = ref('welcome')
const sidebarExpanded = ref(false)
const adminClients = ref([]), adminClientPage = ref(0), adminClientTotalPages = ref(0), adminClientTotal = ref(0)
const adminClientFilter = reactive({ workshopId: '', order: 'asc' })
const login = reactive({ email: '', password: '' })
const forgot = reactive({ email: '' }), reset = reactive({ token: '', password: '', confirm: '' })
const emptyClient = () => ({ fullName: '', alternativeContactName: '', age: '', birthDate: '', phone: '', occupation: '', email: '', street: '', neighborhood: '', municipality: '', state: '', postalCode: '', branchCode: '', workshopId: '' })
const clientForm = reactive(emptyClient())
const emptyWorkshop = () => ({ name: '', legalName: '', rfc: '', phone: '', personalPhone: '', email: '', street: '', neighborhood: '', municipality: '', state: '', postalCode: '' })
const workshopForm = reactive(emptyWorkshop())
const employeeForm = reactive({ name: '', email: '', password: '', role: 'RECEPCIONISTA' })
const addressState = () => ({ municipalities: [], neighborhoods: [], postalCodes: [], suggestions: [], query: '' })
const clientAddress = reactive(addressState()), workshopAddress = reactive(addressState())

const api = async (url, body, method = 'POST') => {
  const headers: Record<string, string> = {}
  if (session.value?.token) headers.Authorization = `Bearer ${session.value.token}`
  if (!(body instanceof FormData)) headers['Content-Type'] = 'application/json'
  const response = await fetch(url, { method, headers, body: body ? (body instanceof FormData ? body : JSON.stringify(body)) : undefined })
  const text = response.status === 204 ? '' : await response.text()
  let data = null
  if (text) { try { data = JSON.parse(text) } catch { data = null } }
  if (!response.ok) throw new Error(data?.message || 'No se pudo completar la operación')
  return data
}
const clear = () => { notice.value = ''; error.value = '' }
const isAdmin = computed(() => session.value?.user?.role === 'ADMIN')
const canManageEmployees = isAdmin
const canManageClients = computed(() => ['ADMIN', 'RECEPCIONISTA'].includes(session.value?.user?.role))
const activeNavigation = computed(() => ({ welcome: 'home', adminClients: 'clients', clientForm: 'clients', vehicleRegistration: 'vehicles', vehicleLookup: 'vehicles', employees: 'team', employeeForm: 'team', workshops: 'workshops', workshopForm: 'workshops', statuses: 'statuses' })[screen.value] || '')
const title = computed(() => ({ login: 'Inicia sesión', forgot: 'Recupera tu acceso', reset: 'Define una contraseña nueva', welcome: 'Acceso autorizado', adminClients: 'Administración de clientes', clientForm: 'Registrar cliente', vehicleRegistration: 'Administración de vehículos', vehicleLookup: 'Consultar vehículo', employees: 'Equipo de trabajo', workshops: 'Talleres', workshopForm: editingWorkshopId.value ? 'Editar taller' : 'Registrar taller', statuses: 'Estatus' })[screen.value])

async function submitLogin() {
  clear(); loading.value = true
  try { const data = await api('/api/auth/login', login); session.value = data; await loadStates(); notice.value = `Bienvenido/a, ${data.user.name}. Rol: ${data.user.role}.`; screen.value = 'welcome' } catch (exception) { error.value = exception.message } finally { loading.value = false }
}
async function submitForgot() {
  clear(); loading.value = true
  try { const data = await api('/api/auth/forgot-password', forgot); notice.value = data.message; devToken.value = data.developmentToken || ''; if (devToken.value) reset.token = devToken.value } catch (exception) { error.value = exception.message } finally { loading.value = false }
}
async function submitReset() {
  clear()
  if (reset.password !== reset.confirm) { error.value = 'Las contraseñas no coinciden.'; return }
  loading.value = true
  try { const data = await api('/api/auth/reset-password', reset); notice.value = data.message; screen.value = 'login' } catch (exception) { error.value = exception.message } finally { loading.value = false }
}
async function loadPhoto(url) {
  const response = await fetch(url, { headers: { Authorization: `Bearer ${session.value.token}` } })
  return response.ok ? URL.createObjectURL(await response.blob()) : ''
}
async function openClientForm(client = null) {
  clear(); editingClientId.value = client?.id || null; clientEditOpen.value = Boolean(client); selectedPhoto.value = null; photoPreview.value = client?.photoPreview || ''
  Object.assign(clientForm, client ? { ...client, branchCode: client.branchCode || '', workshopId: client.workshopId || '' } : emptyClient()); Object.assign(clientAddress, addressState()); if (!client) screen.value = 'clientForm'
  if (!client && adminClientFilter.workshopId && adminClientFilter.workshopId !== 'SIN_TALLER') clientForm.workshopId = adminClientFilter.workshopId
  await hydrateAddress('client')
  await loadActiveWorkshops()
}
async function showAddressError(exception) {
  error.value = exception.message
  await Swal.fire({ icon: 'error', title: 'Dirección no válida', text: exception.message, confirmButtonColor: '#7e22ce' })
}
function addressFor(kind) { return kind === 'client' ? clientForm : workshopForm }
function optionsFor(kind) { return kind === 'client' ? clientAddress : workshopAddress }
async function loadStates() { if (!states.value.length) states.value = await api('/api/codigos-postales/estados', null, 'GET') }
async function onStateChange(kind, preserve = false) {
  const form = addressFor(kind), options = optionsFor(kind), municipality = preserve ? form.municipality : ''
  form.municipality = ''; form.neighborhood = ''; form.postalCode = ''; options.municipalities = []; options.neighborhoods = []; options.postalCodes = []; options.suggestions = []
  options.query = ''
  if (!form.state) return
  try { options.municipalities = await api(`/api/codigos-postales/municipios?estado=${encodeURIComponent(form.state)}`, null, 'GET'); if (preserve && options.municipalities.includes(municipality)) form.municipality = municipality } catch (exception) { await showAddressError(exception) }
}
async function onMunicipalityChange(kind, preserve = false) {
  const form = addressFor(kind), options = optionsFor(kind), neighborhood = preserve ? form.neighborhood : ''
  form.neighborhood = ''; form.postalCode = ''; options.neighborhoods = []; options.postalCodes = []; options.suggestions = []; options.query = ''
  if (!form.state || !form.municipality) return
  try { options.neighborhoods = await api(`/api/codigos-postales/colonias?estado=${encodeURIComponent(form.state)}&municipio=${encodeURIComponent(form.municipality)}`, null, 'GET'); if (preserve && options.neighborhoods.some(item => item.neighborhood === neighborhood)) { form.neighborhood = neighborhood; onNeighborhoodChange(kind, true) } } catch (exception) { await showAddressError(exception) }
}
function onNeighborhoodChange(kind, preservePostal = false) {
  const form = addressFor(kind), options = optionsFor(kind), found = options.neighborhoods.find(item => item.neighborhood === form.neighborhood)
  options.postalCodes = found?.postalCodes || []
  if (!preservePostal) form.postalCode = options.postalCodes.length === 1 ? options.postalCodes[0] : ''
}
async function lookupPostalCode(kind, preserveSelection = false) {
  const form = addressFor(kind), options = optionsFor(kind), previousMunicipality = preserveSelection ? form.municipality : '', previousNeighborhood = preserveSelection ? form.neighborhood : ''
  options.municipalities = []; options.neighborhoods = []; options.postalCodes = []; options.suggestions = []
  if (!preserveSelection) { form.state = ''; form.municipality = ''; form.neighborhood = ''; options.query = '' }
  const postalCode = form.postalCode.trim()
  if (!/^\d{5}$/.test(postalCode)) return
  try {
    const data = await api(`/api/codigos-postales/${postalCode}`, null, 'GET')
    form.state = data.state; options.municipalities = data.municipalities.map(item => item.municipality)
    const municipality = data.municipalities.find(item => item.municipality === previousMunicipality) || (data.municipalities.length === 1 ? data.municipalities[0] : null)
    form.municipality = municipality?.municipality || ''
    options.neighborhoods = municipality ? municipality.neighborhoods.map(neighborhood => ({ neighborhood, postalCodes: [postalCode] })) : []
    if (municipality && municipality.neighborhoods.includes(previousNeighborhood)) { form.neighborhood = previousNeighborhood; options.query = previousNeighborhood; options.postalCodes = [postalCode] } else form.neighborhood = ''
  } catch (exception) { await showAddressError(exception) }
}
async function searchNeighborhood(kind) {
  const form = addressFor(kind), options = optionsFor(kind), query = options.query.trim()
  form.neighborhood = ''; form.postalCode = ''; options.postalCodes = []
  if (!query) { await showNeighborhoodOptions(kind); return }
  if (query.length < 3) { options.suggestions = []; return }
  try { options.suggestions = await api(`/api/codigos-postales/buscar-colonias?q=${encodeURIComponent(query)}${form.state ? `&estado=${encodeURIComponent(form.state)}` : ''}${form.municipality ? `&municipio=${encodeURIComponent(form.municipality)}` : ''}`, null, 'GET') } catch (exception) { await showAddressError(exception) }
}
async function showNeighborhoodOptions(kind) {
  const form = addressFor(kind), options = optionsFor(kind)
  if (!form.municipality) { options.suggestions = []; return }
  if (!options.neighborhoods.length) await onMunicipalityChange(kind, true)
  options.suggestions = options.neighborhoods.slice(0, 20).map(item => ({ neighborhood: item.neighborhood, municipality: form.municipality, state: form.state, postalCodes: item.postalCodes }))
}
async function selectSuggestion(kind, suggestion) {
  const form = addressFor(kind), options = optionsFor(kind)
  form.state = suggestion.state; await onStateChange(kind, true); form.municipality = suggestion.municipality; await onMunicipalityChange(kind, true)
  form.neighborhood = suggestion.neighborhood; options.postalCodes = suggestion.postalCodes; form.postalCode = suggestion.postalCodes.length === 1 ? suggestion.postalCodes[0] : ''; options.query = suggestion.neighborhood; options.suggestions = []
}
async function hydrateAddress(kind) {
  await loadStates()
  const form = addressFor(kind), options = optionsFor(kind)
  if (!form.postalCode) return
  await lookupPostalCode(kind, true)
  options.query = form.neighborhood
}
async function loadWorkshops(showScreen = false) {
  clear()
  if (showScreen) screen.value = 'workshops'
  loading.value = true
  try { workshops.value = await api('/api/talleres', null, 'GET') }
  catch (exception) { error.value = exception.message; await Swal.fire({ icon: 'error', title: 'No se pudieron cargar los talleres', text: exception.message, confirmButtonColor: '#7e22ce' }) }
  finally { loading.value = false }
}
async function loadActiveWorkshops() { activeWorkshops.value = await api('/api/talleres/activos', null, 'GET') }
async function openWorkshopForm(workshop = null) { clear(); editingWorkshopId.value = workshop?.id || null; Object.assign(workshopForm, workshop ? { ...workshop } : emptyWorkshop()); workshopPhoto.value = null; Object.assign(workshopAddress, addressState()); screen.value = 'workshopForm'; await hydrateAddress('workshop') }
function selectWorkshopPhoto(event) { workshopPhoto.value = event.target.files[0] || null }
async function submitWorkshop() { clear(); if (!editingWorkshopId.value && !workshopPhoto.value) return showAddressError(new Error('La fotografía del taller es obligatoria')); const form = new FormData(); Object.entries(workshopForm).forEach(([key, value]) => { if (value !== '') form.append(key, value) }); if (workshopPhoto.value) form.append('photo', workshopPhoto.value); loading.value = true; try { const isEdit = Boolean(editingWorkshopId.value); await api(isEdit ? `/api/talleres/${editingWorkshopId.value}` : '/api/talleres', form, isEdit ? 'PUT' : 'POST'); await Swal.fire({ icon: 'success', title: isEdit ? 'Taller actualizado' : 'Taller registrado', text: 'Los datos se guardaron correctamente.', confirmButtonColor: '#7e22ce' }); await loadWorkshops(true) } catch (exception) { await showAddressError(exception) } finally { loading.value = false } }
async function toggleWorkshopStatus(workshop) { const action = workshop.status === 'ACTIVO' ? 'suspender' : 'reactivar'; const result = await Swal.fire({ icon: 'warning', title: `¿Deseas ${action} este taller?`, text: 'El registro se conservará y podrás cambiar su estatus después.', showCancelButton: true, confirmButtonText: action === 'suspender' ? 'Suspender' : 'Reactivar', cancelButtonText: 'Cancelar', confirmButtonColor: '#7e22ce' }); if (!result.isConfirmed) return; try { await api(`/api/talleres/${workshop.id}/estatus`, null, 'PATCH'); await Swal.fire({ icon: 'success', title: `Taller ${action === 'suspender' ? 'suspendido' : 'reactivado'}`, confirmButtonColor: '#7e22ce' }); await loadWorkshops(true) } catch (exception) { await showAddressError(exception) } }
async function deleteWorkshop(workshop) { const result = await Swal.fire({ icon: 'warning', title: '¿Eliminar taller?', text: 'Esta acción es irreversible. Si tiene clientes asociados, podrás suspenderlo en su lugar.', showCancelButton: true, confirmButtonText: 'Eliminar definitivamente', cancelButtonText: 'Cancelar', confirmButtonColor: '#be123c' }); if (!result.isConfirmed) return; try { await api(`/api/talleres/${workshop.id}`, null, 'DELETE'); await Swal.fire({ icon: 'success', title: 'Taller eliminado', confirmButtonColor: '#7e22ce' }); await loadWorkshops(true) } catch (exception) { await showAddressError(exception) } }
function selectPhoto(event) {
  const [photo] = event.target.files; selectedPhoto.value = photo || null
  if (photo) photoPreview.value = URL.createObjectURL(photo)
}
async function submitClient() {
  clear()
  if (!editingClientId.value && !selectedPhoto.value) { error.value = 'La fotografía es obligatoria.'; return }
  const form = new FormData()
  Object.entries(clientForm).forEach(([key, value]) => { if (value !== '') form.append(key, value) })
  if (selectedPhoto.value) form.append('photo', selectedPhoto.value)
  loading.value = true
  try {
    const isEdit = Boolean(editingClientId.value)
    const savedClient = await api(isEdit ? `/api/clientes/${editingClientId.value}` : '/api/clientes', form, isEdit ? 'PUT' : 'POST')
    await Swal.fire({ icon: 'success', title: isEdit ? 'Cliente actualizado' : 'Cliente registrado', text: 'Los datos se guardaron correctamente.', confirmButtonColor: '#7e22ce' })
    clientEditOpen.value = false
    if (isEdit) await loadAdminClients(adminClientPage.value)
    else {
      screen.value = 'adminClients'
      adminClientFilter.workshopId = String(savedClient.workshopId)
      await loadAdminClients(0)
    }
  } catch (exception) {
    error.value = exception.message
    await Swal.fire({ icon: 'error', title: 'No se pudo guardar el cliente', text: exception.message, confirmButtonColor: '#7e22ce' })
  } finally { loading.value = false }
}
async function loadEmployees() {
  clear(); loading.value = true
  try { employees.value = await api('/api/users', null, 'GET'); screen.value = 'employees' } catch (exception) { error.value = exception.message } finally { loading.value = false }
}
async function toggleEmployeeStatus(employee) { const action = employee.status === 'ACTIVE' ? 'suspender' : 'reactivar'; const result = await Swal.fire({ icon: 'warning', title: `¿Deseas ${action} esta cuenta?`, showCancelButton: true, confirmButtonText: action === 'suspender' ? 'Suspender' : 'Reactivar', cancelButtonText: 'Cancelar', confirmButtonColor: '#7e22ce' }); if (!result.isConfirmed) return; try { await api(`/api/users/${employee.id}/estatus`, null, 'PATCH'); await loadEmployees() } catch (exception) { await showAddressError(exception) } }
async function deleteEmployee(employee) { const result = await Swal.fire({ icon: 'warning', title: '¿Eliminar empleado?', text: 'Esta acción es irreversible.', showCancelButton: true, confirmButtonText: 'Eliminar definitivamente', cancelButtonText: 'Cancelar', confirmButtonColor: '#be123c' }); if (!result.isConfirmed) return; try { await api(`/api/users/${employee.id}`, null, 'DELETE'); await loadEmployees() } catch (exception) { await showAddressError(exception) } }
async function openClientAdministration() { clear(); await loadWorkshops(); screen.value = 'adminClients'; adminClientFilter.workshopId = ''; adminClients.value = []; adminClientPage.value = 0; adminClientTotalPages.value = 0; adminClientTotal.value = 0 }
async function loadAdminClients(page = 0) { if (!adminClientFilter.workshopId) return showAddressError(new Error('Selecciona un taller o la opción Sin taller')); clear(); loading.value = true; try { const data = await api(`/api/administracion/clientes?tallerId=${encodeURIComponent(adminClientFilter.workshopId)}&page=${page}&size=10&order=${adminClientFilter.order}&sort=fullName`, null, 'GET'); adminClients.value = await Promise.all(data.data.map(async client => ({ ...client, photoPreview: await loadPhoto(client.photoUrl) }))); adminClientPage.value = data.page; adminClientTotalPages.value = data.totalPages; adminClientTotal.value = data.totalRecords } catch (exception) { await showAddressError(exception) } finally { loading.value = false } }
function onAdminFilterChange() { loadAdminClients(0) }
async function toggleAdminClientStatus(client) { const action = client.status === 'ACTIVO' ? 'suspender' : 'reactivar'; const endpoint = action === 'suspender' ? 'suspender' : 'reactivar'; const result = await Swal.fire({ icon: 'warning', title: `¿Deseas ${action} este cliente?`, showCancelButton: true, confirmButtonText: action === 'suspender' ? 'Suspender' : 'Reactivar', cancelButtonText: 'Cancelar', confirmButtonColor: '#7e22ce' }); if (!result.isConfirmed) return; try { await api(`/api/administracion/clientes/${client.id}/${endpoint}`, null, 'PATCH'); await loadAdminClients(adminClientPage.value) } catch (exception) { await showAddressError(exception) } }
async function deleteAdminClient(client) { const result = await Swal.fire({ icon: 'warning', title: '¿Eliminar cliente?', text: 'El cliente se conservará con estatus ELIMINADO y seguirá bloqueando duplicados.', showCancelButton: true, confirmButtonText: 'Eliminar', cancelButtonText: 'Cancelar', confirmButtonColor: '#be123c' }); if (!result.isConfirmed) return; try { await api(`/api/administracion/clientes/${client.id}`, null, 'DELETE'); await loadAdminClients(Math.max(0, Math.min(adminClientPage.value, adminClientTotalPages.value - 1))) } catch (exception) { await showAddressError(exception) } }
function openVehicleRegistration(client = null, initialMode = 'form') { vehicleClient.value = client; vehicleInitialMode.value = client ? initialMode : 'list'; vehicleReturnScreen.value = client ? 'adminClients' : 'welcome'; screen.value = 'vehicleRegistration' }
function openClientVehicles(client) { openVehicleRegistration(client, 'client') }
function openVehicleLookup() { vehicleReturnScreen.value = 'vehicleRegistration'; screen.value = 'vehicleLookup' }
async function submitEmployee() {
  clear(); loading.value = true
  try {
    await api('/api/users', employeeForm)
    await Swal.fire({ icon: 'success', title: 'Empleado creado', text: 'La cuenta y el rol se guardaron correctamente.', confirmButtonColor: '#7e22ce' })
    Object.assign(employeeForm, { name: '', email: '', password: '', role: 'RECEPCIONISTA' })
    await loadEmployees()
  } catch (exception) { error.value = exception.message } finally { loading.value = false }
}
async function navigate(target) {
  sidebarExpanded.value = false
  if (target === 'home') { screen.value = 'welcome'; return }
  if (target === 'clients') { await openClientAdministration(); return }
  if (target === 'vehicles') { openVehicleRegistration(); return }
  if (target === 'statuses') { screen.value = 'statuses'; return }
  if (target === 'team') { await loadEmployees(); return }
  if (target === 'workshops') await loadWorkshops(true)
}
function logout() { sidebarExpanded.value = false; session.value = null; employees.value = []; workshops.value = []; activeWorkshops.value = []; states.value = []; screen.value = 'login'; clear() }
</script>

<template>
  <aside v-if="session" :class="sidebarExpanded ? 'w-64' : 'w-20'" class="fixed inset-y-0 left-0 z-50 flex flex-col overflow-hidden border-r border-white/15 bg-[#4C1D95] p-3 text-white shadow-2xl transition-[width] duration-300 ease-out" @mouseenter="sidebarExpanded = true" @mouseleave="sidebarExpanded = false" @click.self="sidebarExpanded = true">
    <div class="mb-8 flex h-11 items-center gap-3 whitespace-nowrap text-lg font-bold"><span class="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-white/14"><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m14.7 6.3 3 3"/><path d="m5 21 4.5-1.2L19.4 9.9a2.1 2.1 0 0 0-3-3L6.5 16.8z"/><path d="m13.5 8.5 2 2"/></svg></span><span :class="sidebarExpanded ? 'opacity-100' : 'opacity-0'" class="transition-opacity duration-200">Taller Morado</span></div>
    <nav class="space-y-2" aria-label="Navegación principal">
      <button title="Inicio" :class="activeNavigation === 'home' ? 'bg-white text-[#7E22CE]' : 'text-white hover:bg-white/14'" class="flex h-11 w-full items-center gap-3 rounded-xl px-2 text-left text-sm font-semibold" @click="navigate('home')"><span class="grid h-11 w-11 shrink-0 place-items-center rounded-xl"><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m3 10 9-7 9 7"/><path d="M5 9v11a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V9"/><path d="M9 21v-7h6v7"/></svg></span><span :class="sidebarExpanded ? 'opacity-100' : 'opacity-0'" class="whitespace-nowrap transition-opacity duration-200">Inicio</span></button>
      <button v-if="canManageClients" title="Clientes" :class="activeNavigation === 'clients' ? 'bg-white text-[#7E22CE]' : 'text-white hover:bg-white/14'" class="flex h-11 w-full items-center gap-3 rounded-xl px-2 text-left text-sm font-semibold" @click="navigate('clients')"><span class="grid h-11 w-11 shrink-0 place-items-center rounded-xl"><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M16 21v-2a4 4 0 0 0-4-4H7a4 4 0 0 0-4 4v2"/><circle cx="9.5" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg></span><span :class="sidebarExpanded ? 'opacity-100' : 'opacity-0'" class="whitespace-nowrap transition-opacity duration-200">Clientes</span></button>
      <button v-if="canManageClients" title="Vehículos" :class="activeNavigation === 'vehicles' ? 'bg-white text-[#7E22CE]' : 'text-white hover:bg-white/14'" class="flex h-11 w-full items-center gap-3 rounded-xl px-2 text-left text-sm font-semibold" @click="navigate('vehicles')"><span class="grid h-11 w-11 shrink-0 place-items-center rounded-xl"><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 17h14v-5l-2-4H7l-2 4z"/><path d="M5 12h14"/><circle cx="8" cy="17" r="1.5"/><circle cx="16" cy="17" r="1.5"/></svg></span><span :class="sidebarExpanded ? 'opacity-100' : 'opacity-0'" class="whitespace-nowrap transition-opacity duration-200">Vehículos</span></button>
      <button v-if="canManageClients" title="Estatus" :class="activeNavigation === 'statuses' ? 'bg-white text-[#7E22CE]' : 'text-white hover:bg-white/14'" class="flex h-11 w-full items-center gap-3 rounded-xl px-2 text-left text-sm font-semibold" @click="navigate('statuses')"><span class="grid h-11 w-11 shrink-0 place-items-center rounded-xl"><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="m8 12 2.5 2.5L16 9"/></svg></span><span :class="sidebarExpanded ? 'opacity-100' : 'opacity-0'" class="whitespace-nowrap transition-opacity duration-200">Estatus</span></button>
      <button v-if="canManageEmployees" title="Equipo" :class="activeNavigation === 'team' ? 'bg-white text-[#7E22CE]' : 'text-white hover:bg-white/14'" class="flex h-11 w-full items-center gap-3 rounded-xl px-2 text-left text-sm font-semibold" @click="navigate('team')"><span class="grid h-11 w-11 shrink-0 place-items-center rounded-xl"><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M14.7 6.3 17.7 3.3a1 1 0 0 1 1.4 0l1.3 1.3a1 1 0 0 1 0 1.4l-3 3"/><path d="m14 7-9.7 9.7a2 2 0 0 0-.5.9L3 21l3.4-.8a2 2 0 0 0 .9-.5L17 10"/><path d="m13 8 3 3"/></svg></span><span :class="sidebarExpanded ? 'opacity-100' : 'opacity-0'" class="whitespace-nowrap transition-opacity duration-200">Equipo</span></button>
      <button v-if="canManageEmployees" title="Talleres" :class="activeNavigation === 'workshops' ? 'bg-white text-[#7E22CE]' : 'text-white hover:bg-white/14'" class="flex h-11 w-full items-center gap-3 rounded-xl px-2 text-left text-sm font-semibold" @click="navigate('workshops')"><span class="grid h-11 w-11 shrink-0 place-items-center rounded-xl"><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 9h18l-2-5H5z"/><path d="M5 9v11h14V9"/><path d="M9 20v-6h6v6"/><path d="M3 9l2 3 2-3 2 3 2-3 2 3 2-3 2 3 2-3"/></svg></span><span :class="sidebarExpanded ? 'opacity-100' : 'opacity-0'" class="whitespace-nowrap transition-opacity duration-200">Talleres</span></button>
    </nav>
    <button title="Cerrar sesión" class="mt-auto flex h-11 w-full items-center gap-3 rounded-xl border border-white/30 px-2 text-left text-sm font-semibold text-white hover:bg-white/14" @click="logout"><span class="grid h-11 w-11 shrink-0 place-items-center rounded-xl"><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M10 17l5-5-5-5"/><path d="M15 12H3"/><path d="M21 19V5a2 2 0 0 0-2-2h-6"/></svg></span><span :class="sidebarExpanded ? 'opacity-100' : 'opacity-0'" class="whitespace-nowrap transition-opacity duration-200">Cerrar sesión</span></button>
  </aside>
  <button v-if="session && sidebarExpanded" class="fixed inset-0 z-40 lg:hidden" aria-label="Cerrar barra lateral" @click="sidebarExpanded = false"></button>
  <main :class="session ? 'lg:grid-cols-1 lg:pl-20' : 'lg:grid-cols-2'" class="grid min-h-screen">
    <section v-if="!session" class="relative hidden overflow-hidden bg-gradient-to-br from-lilac-700 via-purple-700 to-fuchsia-500 p-14 text-white lg:flex lg:flex-col lg:justify-between">
      <div class="flex items-center gap-3 font-semibold"><span class="grid h-10 w-10 place-items-center rounded-xl bg-white/20 text-xl">⚙</span>Taller Morado</div>
      <div><p class="mb-4 text-sm font-semibold tracking-[.2em] text-lilac-100">GESTIÓN INTELIGENTE</p><h1 class="max-w-lg text-5xl font-bold leading-tight">Tu taller, <span class="text-lilac-300">siempre</span> en movimiento.</h1><p class="mt-6 max-w-md text-lg text-white/75">Acceso y clientes reunidos en un solo lugar.</p></div>
      <p class="text-sm text-white/55">Protegemos la información de tu negocio.</p>
    </section>
    <section class="flex items-center justify-center p-6 sm:p-10"><div class="w-full max-w-2xl">
      <div class="mb-10 flex items-center justify-between"><div class="flex items-center gap-2 text-lg font-bold text-lilac-700">⚙ Taller Morado</div></div>
      <div class="mb-8"><p v-if="screen !== 'statuses'" class="text-sm font-semibold text-lilac-700">BIENVENIDO</p><h2 :class="screen === 'statuses' ? '' : 'mt-1'" class="text-3xl font-bold">{{ title }}</h2></div>
      <div v-if="notice" class="mb-5 rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-700">{{ notice }}</div><div v-if="error" class="mb-5 rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700">{{ error }}</div>

      <form v-if="screen === 'login'" class="space-y-5" @submit.prevent="submitLogin"><label class="block text-sm font-medium">Correo electrónico<input v-model="login.email" type="email" required placeholder="nombre@taller.com"></label><label class="block text-sm font-medium">Contraseña<input v-model="login.password" type="password" required placeholder="••••••••"></label><button type="button" class="text-sm font-semibold text-lilac-700" @click="screen = 'forgot'; clear()">¿Olvidaste tu contraseña?</button><button :disabled="loading" class="w-full rounded-xl bg-lilac-700 py-3 font-semibold text-white">{{ loading ? 'Validando…' : 'Iniciar sesión' }}</button></form>
      <form v-else-if="screen === 'forgot'" class="space-y-5" @submit.prevent="submitForgot"><p class="text-sm text-slate-500">Te enviaremos un enlace seguro para restablecer tu contraseña.</p><label class="block text-sm font-medium">Correo electrónico<input v-model="forgot.email" type="email" required></label><button :disabled="loading" class="w-full rounded-xl bg-lilac-700 py-3 font-semibold text-white">Enviar instrucciones</button><div v-if="devToken" class="rounded-xl bg-amber-50 p-3 text-xs text-amber-800">Modo desarrollo — token: <code class="break-all">{{ devToken }}</code><button type="button" class="mt-2 block font-bold underline" @click="screen = 'reset'">Continuar con el token</button></div><button type="button" class="w-full text-sm font-semibold text-lilac-700" @click="screen = 'login'; clear()">Volver</button></form>
      <form v-else-if="screen === 'reset'" class="space-y-4" @submit.prevent="submitReset"><label class="block text-sm font-medium">Token de recuperación<input v-model="reset.token" required></label><label class="block text-sm font-medium">Nueva contraseña<input v-model="reset.password" type="password" minlength="8" required></label><label class="block text-sm font-medium">Confirmar contraseña<input v-model="reset.confirm" type="password" required></label><button :disabled="loading" class="w-full rounded-xl bg-lilac-700 py-3 font-semibold text-white">Guardar contraseña</button></form>
      <div v-else-if="screen === 'welcome'" class="rounded-2xl border border-lilac-100 bg-white p-6 shadow-sm"><div class="mb-4 text-3xl">✓</div><p class="text-slate-600">Tu acceso fue autorizado.</p><p v-if="!canManageEmployees" class="mt-4 text-sm text-slate-500">Tu rol no tiene permisos de administración.</p></div>
      <section v-else-if="screen === 'employees'"><div class="mb-5 flex items-center justify-between"><p class="text-sm text-slate-500">{{ employees.length }} usuario(s)</p><button class="rounded-xl bg-lilac-700 px-4 py-2 text-sm font-semibold text-white" @click="screen = 'employeeForm'; clear()">Nuevo empleado</button></div><div class="grid gap-3"><article v-for="employee in employees" :key="employee.id" class="rounded-2xl border border-lilac-100 bg-white p-4 shadow-sm"><div class="flex items-start justify-between gap-3"><div><p class="font-semibold">{{ employee.name }}</p><p class="text-sm text-slate-500">{{ employee.email }} · {{ employee.role }} · {{ employee.status }}</p></div><div class="flex gap-3 text-sm font-semibold text-lilac-700"><button @click="toggleEmployeeStatus(employee)">{{ employee.status === 'ACTIVE' ? 'Suspender' : 'Reactivar' }}</button><button class="text-rose-700" @click="deleteEmployee(employee)">Eliminar</button></div></div></article></div><button class="mt-6 text-sm font-semibold text-lilac-700" @click="screen = 'welcome'">Volver</button></section>
      <form v-else-if="screen === 'employeeForm'" class="space-y-4" @submit.prevent="submitEmployee"><label class="block text-sm font-medium">Nombre completo<input v-model="employeeForm.name" required maxlength="150"></label><label class="block text-sm font-medium">Correo electrónico<input v-model="employeeForm.email" type="email" required></label><label class="block text-sm font-medium">Contraseña temporal<input v-model="employeeForm.password" type="password" minlength="8" required></label><label class="block text-sm font-medium">Rol<select v-model="employeeForm.role"><option value="GERENTE">Gerente</option><option value="AUXILIAR">Auxiliar</option><option value="MECANICO">Mecánico</option><option value="RECEPCIONISTA">Recepcionista</option></select></label><button :disabled="loading" class="w-full rounded-xl bg-lilac-700 py-3 font-semibold text-white">Crear empleado</button><button type="button" class="w-full text-sm font-semibold text-lilac-700" @click="loadEmployees">Cancelar</button></form>
      <StatusManagement v-else-if="screen === 'statuses'" :api="api" :is-admin="isAdmin" @back="screen = 'welcome'" />
      <VehicleRegistration v-else-if="screen === 'vehicleRegistration'" :key="`${vehicleClient?.id || 'all'}-${vehicleInitialMode}`" :api="api" :client="vehicleClient" :initial-mode="vehicleInitialMode" @back="screen = vehicleReturnScreen" @saved="vehicleClient = null" @lookup="openVehicleLookup" />
      <VehicleLookup v-else-if="screen === 'vehicleLookup'" :api="api" @back="screen = vehicleReturnScreen" />
      <section v-else-if="screen === 'workshops'"><div class="mb-5 flex items-center justify-between"><p class="text-sm text-slate-500">{{ workshops.length }} taller(es)</p><button class="rounded-xl bg-lilac-700 px-4 py-2 text-sm font-semibold text-white" @click="openWorkshopForm()">Nuevo taller</button></div><div class="grid gap-3"><article v-for="workshop in workshops" :key="workshop.id" class="rounded-2xl border border-lilac-100 bg-white p-4 shadow-sm"><div class="flex items-start justify-between gap-3"><div><p class="font-semibold">{{ workshop.name }}</p><p class="text-sm text-slate-500">{{ workshop.legalName }} · {{ workshop.rfc }}</p><p class="mt-1 text-xs" :class="workshop.status === 'ACTIVO' ? 'text-emerald-700' : 'text-amber-700'">{{ workshop.status }}</p></div><div class="flex gap-3 text-sm font-semibold text-lilac-700"><button @click="openWorkshopForm(workshop)">Editar</button><button @click="toggleWorkshopStatus(workshop)">{{ workshop.status === 'ACTIVO' ? 'Suspender' : 'Reactivar' }}</button><button class="text-rose-700" @click="deleteWorkshop(workshop)">Eliminar</button></div></div></article></div><button class="mt-6 text-sm font-semibold text-lilac-700" @click="screen = 'welcome'">Volver</button></section>
      <form v-else-if="screen === 'workshopForm'" class="space-y-4" @submit.prevent="submitWorkshop"><div class="grid gap-4 sm:grid-cols-2"><label class="block text-sm font-medium sm:col-span-2">Nombre del taller<input v-model="workshopForm.name" required></label><label class="block text-sm font-medium sm:col-span-2">Razón social<input v-model="workshopForm.legalName" required></label><label class="block text-sm font-medium">RFC<input v-model="workshopForm.rfc" required pattern="[A-ZÑ&]{3,4}[0-9]{2}(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[A-Z0-9]{3}" @input="workshopForm.rfc = workshopForm.rfc.toUpperCase()"></label><label class="block text-sm font-medium">Teléfono del taller<input v-model="workshopForm.phone" required maxlength="25"></label><label class="block text-sm font-medium">Teléfono personal<input v-model="workshopForm.personalPhone" required maxlength="25"></label><label class="block text-sm font-medium">Email<input v-model="workshopForm.email" type="email" required></label><label class="block text-sm font-medium sm:col-span-2">Foto JPG o PNG, máximo 15 MB <span v-if="!editingWorkshopId" class="text-rose-500">*</span><input accept="image/jpeg,image/png" type="file" :required="!editingWorkshopId" @change="selectWorkshopPhoto"></label><label class="block text-sm font-medium sm:col-span-2">Calle<input v-model="workshopForm.street" required></label><label class="block text-sm font-medium">Código postal<input v-model="workshopForm.postalCode" required maxlength="5" inputmode="numeric" @input="lookupPostalCode('workshop')"></label><label class="block text-sm font-medium">Estado<select v-model="workshopForm.state" required @change="onStateChange('workshop')"><option value="" disabled>Selecciona un estado</option><option v-for="state in states" :key="state" :value="state">{{ state }}</option></select></label><label class="block text-sm font-medium sm:col-span-2">Municipio<select v-model="workshopForm.municipality" :disabled="!workshopAddress.municipalities.length" required @change="onMunicipalityChange('workshop')"><option value="" disabled>Selecciona un municipio</option><option v-for="municipality in workshopAddress.municipalities" :key="municipality" :value="municipality">{{ municipality }}</option></select></label><label class="block text-sm font-medium sm:col-span-2">Colonia<input v-model="workshopAddress.query" required maxlength="100" placeholder="Escribe o selecciona una colonia" @focus="showNeighborhoodOptions('workshop')" @input="searchNeighborhood('workshop')"><div v-if="workshopAddress.suggestions.length" class="mt-1 max-h-40 overflow-auto rounded-xl border border-lilac-200 bg-white"><button v-for="suggestion in workshopAddress.suggestions" :key="`${suggestion.neighborhood}-${suggestion.municipality}-${suggestion.state}-${suggestion.postalCodes.join()}`" type="button" class="block w-full px-3 py-2 text-left text-xs hover:bg-lilac-50" @click="selectSuggestion('workshop', suggestion)">{{ suggestion.neighborhood }} · {{ suggestion.municipality }}, {{ suggestion.state }} · {{ suggestion.postalCodes.join(', ') }}</button></div></label><label v-if="workshopAddress.postalCodes.length > 1" class="block text-sm font-medium sm:col-span-2">Código postal de la colonia<select v-model="workshopForm.postalCode" required><option value="" disabled>Selecciona un código postal</option><option v-for="postalCode in workshopAddress.postalCodes" :key="postalCode" :value="postalCode">{{ postalCode }}</option></select></label></div><button :disabled="loading" class="w-full rounded-xl bg-lilac-700 py-3 font-semibold text-white">{{ editingWorkshopId ? 'Guardar cambios' : 'Registrar taller' }}</button><button type="button" class="w-full text-sm font-semibold text-lilac-700" @click="loadWorkshops(true)">Cancelar</button></form>
      <section v-else-if="screen === 'adminClients'" class="space-y-4">
        <div class="sticky top-0 z-20 -mx-2 flex flex-wrap items-end gap-3 border-b border-lilac-100 bg-lilac-50/95 px-2 pb-3 pt-2 backdrop-blur"><label class="block min-w-52 flex-1 text-sm font-medium">Taller<select v-model="adminClientFilter.workshopId" required @change="onAdminFilterChange"><option value="" disabled>Selecciona un taller</option><option value="SIN_TALLER">Sin taller</option><option v-for="workshop in workshops" :key="workshop.id" :value="String(workshop.id)">{{ workshop.name }}{{ workshop.status === 'ACTIVO' ? '' : ' (SUSPENDIDO)' }}</option></select></label><label class="block min-w-40 text-sm font-medium">Orden<select v-model="adminClientFilter.order" @change="loadAdminClients(0)"><option value="asc">Nombre: A-Z</option><option value="desc">Nombre: Z-A</option></select></label><button class="rounded-xl bg-lilac-700 px-4 py-3 text-sm font-semibold text-white" @click="openClientForm()">Nuevo cliente</button></div>
        <p class="text-sm text-slate-500">{{ adminClientTotal }} cliente(s) · página {{ adminClientPage + 1 }} de {{ Math.max(1, adminClientTotalPages) }}</p><div v-if="!adminClients.length" class="rounded-xl border border-dashed border-lilac-300 p-8 text-center text-slate-500">Selecciona un taller para consultar sus clientes.</div><div v-else class="grid gap-3"><article v-for="client in adminClients" :key="client.id" class="flex items-center gap-4 rounded-2xl border border-lilac-100 bg-white p-4 shadow-sm"><img v-if="client.photoPreview" :src="client.photoPreview" :alt="client.fullName" class="h-14 w-14 rounded-xl object-cover"><div class="min-w-0 flex-1"><p class="truncate font-semibold">{{ client.fullName }}</p><p class="truncate text-sm text-slate-500">{{ client.email }} · {{ client.phone }} · {{ client.status }}</p></div><div class="flex flex-wrap gap-3 text-sm font-semibold text-lilac-700"><button @click="openClientForm(client)">Editar</button><button @click="openClientVehicles(client)">Vehículos</button><button v-if="client.status === 'SUSPENDIDO'" @click="toggleAdminClientStatus(client)">Reactivar</button><button v-if="client.status === 'ACTIVO' && canManageClients" @click="openVehicleRegistration(client)">Asociar vehículo</button><button v-if="isAdmin" class="text-rose-700" @click="deleteAdminClient(client)">Eliminar</button></div></article></div><div class="flex justify-between"><button :disabled="adminClientPage === 0" class="text-sm font-semibold text-lilac-700 disabled:text-slate-300" @click="loadAdminClients(adminClientPage - 1)">Anterior</button><button :disabled="adminClientPage + 1 >= adminClientTotalPages" class="text-sm font-semibold text-lilac-700 disabled:text-slate-300" @click="loadAdminClients(adminClientPage + 1)">Siguiente</button></div>
        <button class="text-sm font-semibold text-lilac-700" @click="screen = 'welcome'">Volver</button>
        <ClientEditModal v-if="clientEditOpen" :form="clientForm" :address="clientAddress" :states="states" :workshops="activeWorkshops" :photo-preview="photoPreview" :loading="loading" :lookup-postal-code="lookupPostalCode" :on-state-change="onStateChange" :on-municipality-change="onMunicipalityChange" :search-neighborhood="searchNeighborhood" :show-neighborhood-options="showNeighborhoodOptions" :select-suggestion="selectSuggestion" @save="submitClient" @cancel="clientEditOpen = false" @photo="selectPhoto" />
      </section>
      <form v-else-if="screen === 'clientForm'" class="space-y-4" @submit.prevent="submitClient">
        <div class="grid gap-4 sm:grid-cols-2">
          <label class="block text-sm font-medium sm:col-span-2">Nombre completo<input v-model="clientForm.fullName" required maxlength="150"></label>
          <label class="block text-sm font-medium sm:col-span-2">Contacto alternativo<input v-model="clientForm.alternativeContactName" maxlength="150"></label>
          <label class="block text-sm font-medium">Edad<input v-model="clientForm.age" type="number" min="0" max="130" required></label>
          <label class="block text-sm font-medium">Fecha de nacimiento<input v-model="clientForm.birthDate" type="date" required></label>
          <label class="block text-sm font-medium">Teléfono personal<input v-model="clientForm.phone" required maxlength="25"></label>
          <label class="block text-sm font-medium">Trabajo<input v-model="clientForm.occupation" required maxlength="150"></label>
          <label class="block text-sm font-medium sm:col-span-2">Email personal o laboral<input v-model="clientForm.email" type="email" required maxlength="150"></label>
          <label class="block text-sm font-medium sm:col-span-2">Fotografía JPG o PNG, máximo 20 MB <span v-if="!editingClientId" class="text-rose-500">*</span><input accept="image/jpeg,image/png" type="file" :required="!editingClientId" @change="selectPhoto"></label>
          <img v-if="photoPreview" :src="photoPreview" alt="Vista previa" class="h-24 w-24 rounded-xl object-cover">
          <label class="block text-sm font-medium sm:col-span-2">Calle<input v-model="clientForm.street" required maxlength="150"></label>
          <label class="block text-sm font-medium">Código postal<input v-model="clientForm.postalCode" inputmode="numeric" pattern="[0-9]{5}" required maxlength="5" @input="lookupPostalCode('client')"></label>
          <label class="block text-sm font-medium">Estado<select v-model="clientForm.state" required @change="onStateChange('client')"><option value="" disabled>Selecciona un estado</option><option v-for="state in states" :key="state" :value="state">{{ state }}</option></select></label>
          <label class="block text-sm font-medium sm:col-span-2">Municipio<select v-model="clientForm.municipality" :disabled="!clientAddress.municipalities.length" required @change="onMunicipalityChange('client')"><option value="" disabled>Selecciona un municipio</option><option v-for="municipality in clientAddress.municipalities" :key="municipality" :value="municipality">{{ municipality }}</option></select></label>
          <label class="block text-sm font-medium sm:col-span-2">Colonia<input v-model="clientAddress.query" required maxlength="100" placeholder="Escribe o selecciona una colonia" @focus="showNeighborhoodOptions('client')" @input="searchNeighborhood('client')"><div v-if="clientAddress.suggestions.length" class="mt-1 max-h-40 overflow-auto rounded-xl border border-lilac-200 bg-white"><button v-for="suggestion in clientAddress.suggestions" :key="`${suggestion.neighborhood}-${suggestion.municipality}-${suggestion.state}-${suggestion.postalCodes.join()}`" type="button" class="block w-full px-3 py-2 text-left text-xs hover:bg-lilac-50" @click="selectSuggestion('client', suggestion)">{{ suggestion.neighborhood }} · {{ suggestion.municipality }}, {{ suggestion.state }} · {{ suggestion.postalCodes.join(', ') }}</button></div></label>
          <label v-if="clientAddress.postalCodes.length > 1" class="block text-sm font-medium sm:col-span-2">Código postal de la colonia<select v-model="clientForm.postalCode" required><option value="" disabled>Selecciona un código postal</option><option v-for="postalCode in clientAddress.postalCodes" :key="postalCode" :value="postalCode">{{ postalCode }}</option></select></label>
          <label class="block text-sm font-medium sm:col-span-2">Taller<select v-model="clientForm.workshopId" :required="!editingClientId"><option value="" disabled>Selecciona un taller</option><option v-for="workshop in activeWorkshops" :key="workshop.id" :value="workshop.id">{{ workshop.name }}</option></select></label>
        </div>
        <button :disabled="loading" class="w-full rounded-xl bg-lilac-700 py-3 font-semibold text-white">{{ loading ? 'Guardando…' : editingClientId ? 'Guardar cambios' : 'Registrar cliente' }}</button>
        <button type="button" class="w-full text-sm font-semibold text-lilac-700" @click="screen = 'adminClients'">Cancelar</button>
      </form>
      <p class="mt-10 text-center text-xs text-slate-400">Acceso protegido · Contraseñas cifradas con BCrypt</p>
    </div></section>
  </main>
</template>
