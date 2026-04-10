const BLOOD = ['A', 'B', 'AB', 'O']

export function isHealthProfileMandatoryComplete(data) {
  if (!data) return false
  const gender = data.gender
  if (gender !== 1 && gender !== 2) return false
  if (data.age == null || data.age === '') return false
  if (data.height == null || data.height === '') return false
  if (data.weight == null || data.weight === '') return false
  const bt = String(data.bloodType || '').trim()
  if (!bt || !BLOOD.includes(bt)) return false
  return true
}
