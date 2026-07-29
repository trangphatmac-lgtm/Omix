const platform = navigator.userAgentData?.platform || navigator.platform || '';

export const isWindows = /win/i.test(platform);
export const isMac = /mac/i.test(platform);
export const isLinux = /linux/i.test(platform);
export const isDevelopment = process.env.NODE_ENV === 'development';

// Omix owns the host lifecycle. Electron-only integrations stay disabled.
export const isCreateTray = false;
export const isCreateMpris = false;
