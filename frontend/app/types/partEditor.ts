export interface PartEditorItem {
  key: number
  partId: number | '' | 'custom'
  customName: string
  searchQuery: string
  quantity: number
  unitPrice: number
}
