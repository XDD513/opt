import request from './request'

/**
 * 获取省份列表
 */
export function getProvinceList() {
  return request({
    url: '/area/provinces',
    method: 'get'
  })
}

/**
 * 根据省份ID获取城市列表
 * @param {string} provinceId 省份ID
 */
export function getCityList(provinceId) {
  return request({
    url: '/area/cities',
    method: 'get',
    params: { provinceId }
  })
}

/**
 * 根据城市ID获取区县列表
 * @param {string} cityId 城市ID
 */
export function getCountyList(cityId) {
  return request({
    url: '/area/counties',
    method: 'get',
    params: { cityId }
  })
}

