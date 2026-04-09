import request from './request'

/**
 * 生成OSS签名URL
 */
export function generatePresignedUrl(url, expirationMinutes = 60) {
  return request({
    url: '/oss/presigned-url',
    method: 'get',
    params: {
      url,
      expirationMinutes
    }
  })
}

