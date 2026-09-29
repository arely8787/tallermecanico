<script setup>
import { computed, reactive, ref } from 'vue'
import Swal from 'sweetalert2'

const screen = ref('login'), notice = ref(''), error = ref(''), devToken = ref(''), loading = ref(false)
const session = ref(null), clients = ref([]), editingClientId = ref(null), selectedPhoto = ref(null), photoPreview = ref('')
const login = reactive({ email: '', password: '' })
const register = reactive({ name: '', email: '', password: '', confirm: '' })
const forgot = reactive({ email: '' }), reset = reactive({ token: '', password: '', confirm: '' })
const emptyClient = () => ({ fullName: '', alternativeContactName: '', age: '', birthDate: '', phone: '', occupation: '', email: '', street: '', neighborhood: '', municipality: '', state: '', postalCode: '', branchCode: '' })
const clientForm = reactive(emptyClient())

const api = async (url, body, method = 'POST') => {
  const headers = {}
  if (session.value?.token) headers.Authorization = `Bearer ${session.value.token}`
  if (!(body instanceof FormData)) headers['Content-Type'] = 'application/json'
  const response = await fetch(url, { method, headers, body: body ? (body instanceof FormData ? body : JSON.stringify(body)) : undefined })
  const data = response.status === 204 ? null : await response.json()
  if (!response.ok) throw new Error(data?.message || 'No se pudo completar la operación')
  return data
}
const clear = () => { notice.value = ''; error.value = '' }
const canManageClients = computed(() => ['ADMIN', 'RECEPCIONISTA'].includes(session.value?.user?.role))
const title = computed(() => ({ login: 'Inicia sesión', register: 'Crea tu cuenta', forgot: 'Recupera tu acceso', reset: 'Define una contraseña nueva', welcome: 'Acceso autorizado', clients: 'Clientes', clientForm: editingClientId.value ? 'Editar cliente' : 'Registrar cliente' })[screen.value])

async function submitLogin() {
  clear(); loading.value = true
  try { const data = await api('/api/auth/login', login); session.value = data; notice.value = `Bienvenido/a, ${data.user.name}. Rol: ${data.user.role}.`; screen.value = 'welcome' } catch (exception) { error.value = exception.message } finally { loading.value = false }
}
async function submitRegister() {
  clear()
  if (register.password !== register.confirm) { error.value = 'Las contraseñas no coinciden.'; return }
  loading.value = true
  try { const data = await api('/api/auth/register', register); notice.value = data.message; screen.value = 'login' } catch (exception) { error.value = exception.message } finally { loading.value = false }
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
async function loadClients() {
  clear(); loading.value = true
  try { const data = await api('/api/clientes', null, 'GET'); clients.value = await Promise.all(data.map(async client => ({ ...client, photoPreview: await loadPhoto(client.photoUrl) }))); screen.value = 'clients' } catch (exception) { error.value = exception.message } finally { loading.value = false }
}
function openClientForm(client = null) {
  clear(); editingClientId.value = client?.id || null; selectedPhoto.value = null; photoPreview.value = client?.photoPreview || ''
  Object.assign(clientForm, client ? { ...client, branchCode: client.branchCode || '' } : emptyClient()); screen.value = 'clientForm'
}
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
    await api(isEdit ? `/api/clientes/${editingClientId.value}` : '/api/clientes', form, isEdit ? 'PUT' : 'POST')
    await Swal.fire({ icon: 'success', title: isEdit ? 'Cliente actualizado' : 'Cliente registrado', text: 'Los datos se guardaron correctamente.', confirmButtonColor: '#7e22ce' })
    await loadClients()
  } catch (exception) { error.value = exception.message } finally { loading.value = false }
}
function logout() { session.value = null; clients.value = []; screen.value = 'login'; clear() }
</script>

<template>
  <main class="grid min-h-screen lg:grid-cols-2">
    <section class="relative hidden overflow-hidden bg-gradient-to-br from-lilac-700 via-purple-700 to-fuchsia-500 p-14 text-white lg:flex lg:flex-col lg:justify-between">
      <div class="flex items-center gap-3 font-semibold"><span class="grid h-10 w-10 place-items-center rounded-xl bg-white/20 text-xl">⚙</span>Taller Morado</div>
      <div><p class="mb-4 text-sm font-semibold tracking-[.2em] text-lilac-100">GESTIÓN INTELIGENTE</p><h1 class="max-w-lg text-5xl font-bold leading-tight">Tu taller, <span class="text-lilac-300">siempre</span> en movimiento.</h1><p class="mt-6 max-w-md text-lg text-white/75">Acceso y clientes reunidos en un solo lugar.</p></div>
      <p class="text-sm text-white/55">Protegemos la información de tu negocio.</p>
    </section>
    <section class="flex items-center justify-center p-6 sm:p-10"><div class="w-full max-w-2xl">
      <div class="mb-10 flex items-center justify-between"><div class="flex items-center gap-2 text-lg font-bold text-lilac-700">⚙ Taller Morado</div><button v-if="session" class="text-sm font-semibold text-lilac-700" @click="logout">Cerrar sesión</button></div>
      <div class="mb-8"><p class="text-sm font-semibold text-lilac-700">BIENVENIDO</p><h2 class="mt-1 text-3xl font-bold">{{ title }}</h2><p v-if="screen === 'register'" class="mt-2 text-sm text-slate-500">Esta pantalla crea una cuenta de acceso; el registro de clientes está reservado a administración y recepción.</p></div>
      <div v-if="notice" class="mb-5 rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-700">{{ notice }}</div><div v-if="error" class="mb-5 rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700">{{ error }}</div>

      <form v-if="screen === 'login'" class="space-y-5" @submit.prevent="submitLogin"><label class="block text-sm font-medium">Correo electrónico<input v-model="login.email" type="email" required placeholder="nombre@taller.com"></label><label class="block text-sm font-medium">Contraseña<input v-model="login.password" type="password" required placeholder="••••••••"></label><button type="button" class="text-sm font-semibold text-lilac-700" @click="screen = 'forgot'; clear()">¿Olvidaste tu contraseña?</button><button :disabled="loading" class="w-full rounded-xl bg-lilac-700 py-3 font-semibold text-white">{{ loading ? 'Validando…' : 'Iniciar sesión' }}</button><p class="text-center text-sm text-slate-500">¿Aún no tienes cuenta? <button type="button" class="font-semibold text-lilac-700" @click="screen = 'register'; clear()">Regístrate</button></p></form>
      <form v-else-if="screen === 'register'" class="space-y-4" @submit.prevent="submitRegister"><label class="block text-sm font-medium">Nombre completo<input v-model="register.name" required></label><label class="block text-sm font-medium">Correo electrónico<input v-model="register.email" type="email" required></label><label class="block text-sm font-medium">Contraseña<input v-model="register.password" type="password" minlength="8" required></label><label class="block text-sm font-medium">Confirmar contraseña<input v-model="register.confirm" type="password" required></label><button :disabled="loading" class="w-full rounded-xl bg-lilac-700 py-3 font-semibold text-white">Crear cuenta</button><button type="button" class="w-full text-sm font-semibold text-lilac-700" @click="screen = 'login'; clear()">Volver a iniciar sesión</button></form>
      <form v-else-if="screen === 'forgot'" class="space-y-5" @submit.prevent="submitForgot"><p class="text-sm text-slate-500">Te enviaremos un enlace seguro para restablecer tu contraseña.</p><label class="block text-sm font-medium">Correo electrónico<input v-model="forgot.email" type="email" required></label><button :disabled="loading" class="w-full rounded-xl bg-lilac-700 py-3 font-semibold text-white">Enviar instrucciones</button><div v-if="devToken" class="rounded-xl bg-amber-50 p-3 text-xs text-amber-800">Modo desarrollo — token: <code class="break-all">{{ devToken }}</code><button type="button" class="mt-2 block font-bold underline" @click="screen = 'reset'">Continuar con el token</button></div><button type="button" class="w-full text-sm font-semibold text-lilac-700" @click="screen = 'login'; clear()">Volver</button></form>
      <form v-else-if="screen === 'reset'" class="space-y-4" @submit.prevent="submitReset"><label class="block text-sm font-medium">Token de recuperación<input v-model="reset.token" required></label><label class="block text-sm font-medium">Nueva contraseña<input v-model="reset.password" type="password" minlength="8" required></label><label class="block text-sm font-medium">Confirmar contraseña<input v-model="reset.confirm" type="password" required></label><button :disabled="loading" class="w-full rounded-xl bg-lilac-700 py-3 font-semibold text-white">Guardar contraseña</button></form>
      <div v-else-if="screen === 'welcome'" class="rounded-2xl border border-lilac-100 bg-white p-6 shadow-sm"><div class="mb-4 text-3xl">✓</div><p class="text-slate-600">Tu acceso fue autorizado.</p><button v-if="canManageClients" class="mt-6 rounded-xl bg-lilac-700 px-5 py-3 text-sm font-semibold text-white" @click="loadClients">Gestionar clientes</button><p v-else class="mt-4 text-sm text-slate-500">Tu rol no tiene permiso para registrar clientes.</p></div>
      <section v-else-if="screen === 'clients'"><div class="mb-5 flex items-center justify-between"><p class="text-sm text-slate-500">{{ clients.length }} cliente(s) registrado(s)</p><button class="rounded-xl bg-lilac-700 px-4 py-2 text-sm font-semibold text-white" @click="openClientForm()">Nuevo cliente</button></div><div v-if="!clients.length" class="rounded-xl border border-dashed border-lilac-300 p-8 text-center text-slate-500">Aún no hay clientes registrados.</div><div v-else class="grid gap-3"><article v-for="client in clients" :key="client.id" class="flex items-center gap-4 rounded-2xl border border-lilac-100 bg-white p-4 shadow-sm"><img v-if="client.photoPreview" :src="client.photoPreview" :alt="client.fullName" class="h-14 w-14 rounded-xl object-cover"><div class="min-w-0 flex-1"><p class="truncate font-semibold">{{ client.fullName }}</p><p class="truncate text-sm text-slate-500">{{ client.email }} · {{ client.phone }}</p></div><button class="text-sm font-semibold text-lilac-700" @click="openClientForm(client)">Editar</button></article></div><button class="mt-6 text-sm font-semibold text-lilac-700" @click="screen = 'welcome'">Volver</button></section>
      <form v-else-if="screen === 'clientForm'" class="space-y-4" @submit.prevent="submitClient"><div class="grid gap-4 sm:grid-cols-2"><label class="block text-sm font-medium sm:col-span-2">Nombre completo<input v-model="clientForm.fullName" required maxlength="150"></label><label class="block text-sm font-medium sm:col-span-2">Contacto alternativo<input v-model="clientForm.alternativeContactName" maxlength="150"></label><label class="block text-sm font-medium">Edad<input v-model="clientForm.age" type="number" min="0" max="130" required></label><label class="block text-sm font-medium">Fecha de nacimiento<input v-model="clientForm.birthDate" type="date" required></label><label class="block text-sm font-medium">Teléfono personal<input v-model="clientForm.phone" required maxlength="25"></label><label class="block text-sm font-medium">Trabajo<input v-model="clientForm.occupation" required maxlength="150"></label><label class="block text-sm font-medium sm:col-span-2">Email personal o laboral<input v-model="clientForm.email" type="email" required maxlength="150"></label><label class="block text-sm font-medium sm:col-span-2">Fotografía JPG o PNG <span v-if="!editingClientId" class="text-rose-500">*</span><input accept="image/jpeg,image/png" type="file" :required="!editingClientId" @change="selectPhoto"></label><img v-if="photoPreview" :src="photoPreview" alt="Vista previa" class="h-24 w-24 rounded-xl object-cover"><label class="block text-sm font-medium sm:col-span-2">Calle<input v-model="clientForm.street" required maxlength="150"></label><label class="block text-sm font-medium">Colonia<input v-model="clientForm.neighborhood" required maxlength="100"></label><label class="block text-sm font-medium">Municipio<input v-model="clientForm.municipality" required maxlength="100"></label><label class="block text-sm font-medium">Estado<input v-model="clientForm.state" required maxlength="100"></label><label class="block text-sm font-medium">Código postal<input v-model="clientForm.postalCode" pattern="[0-9]{5}" required maxlength="5"></label><label class="block text-sm font-medium">Sucursal futura<select v-model="clientForm.branchCode"><option value="">Sin asignar</option><option value="A">Sucursal A</option><option value="B">Sucursal B</option></select></label></div><button :disabled="loading" class="w-full rounded-xl bg-lilac-700 py-3 font-semibold text-white">{{ loading ? 'Guardando…' : editingClientId ? 'Guardar cambios' : 'Registrar cliente' }}</button><button type="button" class="w-full text-sm font-semibold text-lilac-700" @click="loadClients">Cancelar</button></form>
      <p class="mt-10 text-center text-xs text-slate-400">Acceso protegido · Contraseñas cifradas con BCrypt</p>
    </div></section>
  </main>
</template>
