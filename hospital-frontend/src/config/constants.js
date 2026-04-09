import { getAppConfig } from '@/config/runtimeConfig'

const FALLBACK_AVATARS = {
  PATIENT: 'https://api.dicebear.com/7.x/thumbs/svg?seed=patient',
  SYSTEM: 'https://api.dicebear.com/7.x/shapes/svg?seed=assistant'
}

export const getAvatarConfig = () => {
  const appConfig = getAppConfig()
  return {
    PATIENT: appConfig?.defaultAvatars?.patient || FALLBACK_AVATARS.PATIENT,
    SYSTEM: appConfig?.defaultAvatars?.system || FALLBACK_AVATARS.SYSTEM
  }
}

export const DEFAULT_AVATAR =
  'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="96" height="96" viewBox="0 0 96 96"><defs><linearGradient id="g" x1="0%" y1="0%" x2="0%" y2="100%"><stop offset="0%" stop-color="%23dff5ff"/><stop offset="100%" stop-color="%23b8e5ff"/></linearGradient></defs><rect width="96" height="96" rx="48" fill="url(%23g)"/><path d="M48 26c-8.008 0-14.5 6.492-14.5 14.5S39.992 55 48 55s14.5-6.492 14.5-14.5S56.008 26 48 26zm0 38c-11.791 0-21.5 7.909-21.5 17.667V84h43v-2.333C69.5 71.909 59.791 64 48 64z" fill="%2360c4ff"/></svg>'

