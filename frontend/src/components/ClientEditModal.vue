<script setup lang="ts">
interface ClientForm {
  fullName: string
  alternativeContactName: string
  age: string
  birthDate: string
  phone: string
  occupation: string
  email: string
  street: string
  postalCode: string
  state: string
  municipality: string
}

interface NeighborhoodSuggestion {
  neighborhood: string
  municipality: string
  state: string
  postalCodes: string[]
}

interface AddressOptions {
  municipalities: string[]
  neighborhoods: unknown[]
  postalCodes: string[]
  suggestions: NeighborhoodSuggestion[]
  query: string
}

defineProps<{
  form: ClientForm
  address: AddressOptions
  states: string[]
  workshops: unknown[]
  photoPreview?: string
  loading?: boolean
  lookupPostalCode: (kind: string) => void
  onStateChange: (kind: string) => void
  onMunicipalityChange: (kind: string) => void
  searchNeighborhood: (kind: string) => void
  showNeighborhoodOptions: (kind: string) => void
  selectSuggestion: (kind: string, suggestion: NeighborhoodSuggestion) => void
}>()

defineEmits<{ save: []; cancel: []; photo: [event: Event] }>()
</script>

<template><div class="fixed inset-0 z-[60] flex items-start justify-center overflow-y-auto bg-slate-950/45 p-4 sm:items-center"><form class="w-full max-w-3xl space-y-4 rounded-2xl bg-white p-6 shadow-2xl" @submit.prevent="$emit('save')"><div class="flex items-center justify-between"><h3 class="text-xl font-bold text-ink">Editar cliente</h3><button type="button" class="text-sm font-semibold text-lilac-700" @click="$emit('cancel')">Cerrar</button></div><div class="grid gap-4 sm:grid-cols-2"><label class="block text-sm font-medium sm:col-span-2">Nombre completo<input v-model="form.fullName" required maxlength="150"></label><label class="block text-sm font-medium sm:col-span-2">Contacto alternativo<input v-model="form.alternativeContactName" maxlength="150"></label><label class="block text-sm font-medium">Edad<input v-model="form.age" type="number" min="0" max="130" required></label><label class="block text-sm font-medium">Fecha de nacimiento<input v-model="form.birthDate" type="date" required></label><label class="block text-sm font-medium">Teléfono personal<input v-model="form.phone" required maxlength="25"></label><label class="block text-sm font-medium">Trabajo<input v-model="form.occupation" required maxlength="150"></label><label class="block text-sm font-medium sm:col-span-2">Email personal o laboral<input v-model="form.email" type="email" required maxlength="150"></label><label class="block text-sm font-medium sm:col-span-2">Fotografía JPG o PNG, máximo 20 MB<input accept="image/jpeg,image/png" type="file" @change="$emit('photo', $event)"></label><img v-if="photoPreview" :src="photoPreview" alt="Vista previa" class="h-20 w-20 rounded-xl object-cover"><label class="block text-sm font-medium sm:col-span-2">Calle<input v-model="form.street" required maxlength="150"></label><label class="block text-sm font-medium">Código postal<input v-model="form.postalCode" inputmode="numeric" pattern="[0-9]{5}" required maxlength="5" @input="lookupPostalCode('client')"></label><label class="block text-sm font-medium">Estado<select v-model="form.state" required @change="onStateChange('client')"><option value="" disabled>Selecciona un estado</option><option v-for="state in states" :key="state" :value="state">{{ state }}</option></select></label><label class="block text-sm font-medium sm:col-span-2">Municipio<select v-model="form.municipality" :disabled="!address.municipalities.length" required @change="onMunicipalityChange('client')"><option value="" disabled>Selecciona un municipio</option><option v-for="municipality in address.municipalities" :key="municipality" :value="municipality">{{ municipality }}</option></select></label><label class="block text-sm font-medium sm:col-span-2">Colonia<input v-model="address.query" required maxlength="100" placeholder="Escribe o selecciona una colonia" @focus="showNeighborhoodOptions('client')" @input="searchNeighborhood('client')"><div v-if="address.suggestions.length" class="mt-1 max-h-40 overflow-auto rounded-xl border border-lilac-200 bg-white"><button v-for="suggestion in address.suggestions" :key="`${suggestion.neighborhood}-${suggestion.municipality}-${suggestion.state}-${suggestion.postalCodes.join()}`" type="button" class="block w-full px-3 py-2 text-left text-xs hover:bg-lilac-50" @click="selectSuggestion('client', suggestion)">{{ suggestion.neighborhood }} · {{ suggestion.municipality }}, {{ suggestion.state }} · {{ suggestion.postalCodes.join(', ') }}</button></div></label><label v-if="address.postalCodes.length > 1" class="block text-sm font-medium sm:col-span-2">Código postal de la colonia<select v-model="form.postalCode" required><option value="" disabled>Selecciona un código postal</option><option v-for="postalCode in address.postalCodes" :key="postalCode" :value="postalCode">{{ postalCode }}</option></select></label></div><div class="flex gap-3"><button :disabled="loading" class="flex-1 rounded-xl bg-lilac-700 py-3 font-semibold text-white">Guardar cambios</button><button type="button" class="rounded-xl px-4 text-sm font-semibold text-lilac-700" @click="$emit('cancel')">Cancelar</button></div></form></div></template>
