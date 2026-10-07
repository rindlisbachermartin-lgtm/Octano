<script setup lang="ts">
import { CalendarDate, parseDate, type DateValue } from '@internationalized/date'
import { CalendarDays, ChevronDown } from 'lucide-vue-next'

const props = defineProps<{
  modelValue: string
  label: string
  compact?: boolean
  type?: 'date' | 'month'
  yearSelection?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const open = ref(false)
const { today } = useWorkshopDay()

const calendarValue = computed<DateValue | undefined>({
  get() {
    if (!props.modelValue) return undefined
    const [year, month, day] = props.modelValue.split('-').map(Number)
    return new CalendarDate(year, month, day)
  },
  set(value) {
    if (!value) return
    emit('update:modelValue', value.toString())
    open.value = false
  }
})

const displayDate = computed(() => {
  if (!props.modelValue) return 'Seleccionar fecha'
  return new Intl.DateTimeFormat('es-AR', { day: props.type === 'month' ? undefined : 'numeric', month: 'short', year: 'numeric' })
    .format(new Date(`${props.modelValue}T12:00:00`))
})
</script>

<template>
  <div class="date-picker">
    <span class="date-picker-label">{{ label }}</span>
    <UPopover v-model:open="open" :content="{ align: 'start', side: 'bottom' }" :ui="{ content: compact ? 'octano-calendar-popover octano-calendar-popover--compact' : 'octano-calendar-popover' }">
      <button type="button" class="date-picker-trigger" :class="{ 'date-picker-trigger--compact': compact }" :aria-label="`${label}: ${displayDate}`">
        <CalendarDays :size="17" />
        <span>{{ displayDate }}</span>
        <ChevronDown :size="15" />
      </button>
      <template #content>
        <UCalendar :key="type || 'date'" v-model="calendarValue" :default-placeholder="parseDate(today)" :type="type || 'date'" class="octano-calendar" :class="{ 'octano-calendar--compact': compact }" color="primary" locale="es-AR" :week-starts-on="0" weekday-format="narrow" :view-control="!!yearSelection" :year-controls="!!yearSelection" :prevent-deselect="true">
          <template #month-cell="{ month }"><span class="calendar-period-cell">{{ new Intl.DateTimeFormat('es-AR', { month: 'short' }).format(month.toDate('America/Argentina/Buenos_Aires')) }}</span></template>
          <template #year-cell="{ year }"><span class="calendar-period-cell">{{ year.year }}</span></template>
        </UCalendar>
      </template>
    </UPopover>
  </div>
</template>

<style scoped>
.date-picker { display: flex; flex-direction: column; gap: 7px; min-width: 0; }
.date-picker-label { font-size: 12px; font-weight: 550; }
.date-picker-trigger {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  min-height: 41px;
  padding: 9px 11px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #fff;
  color: #0f172a;
  text-align: left;
  font-size: 12px;
}
.date-picker-trigger span { flex: 1; }
.date-picker-trigger:hover { border-color: #94a3b8; }
.date-picker-trigger:focus-visible { outline: 3px solid #93c5fd; outline-offset: 2px; }
:global(html.dark) .date-picker-trigger {
  background: #252528;
  color: #f5f5f7;
  border-color: rgba(255, 255, 255, .18);
}
:global(html.dark) .date-picker-label { color: #f5f5f7; }
</style>
