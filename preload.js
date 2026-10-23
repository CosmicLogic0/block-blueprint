'use strict';
const { contextBridge, ipcRenderer, webFrame } = require('electron');

contextBridge.exposeInMainWorld('api', {
  assetBase: 'mcassets://x/',
  status: () => ipcRenderer.invoke('assets:status'),
  download: () => ipcRenderer.invoke('assets:download'),
  useInstalled: () => ipcRenderer.invoke('assets:installed'),
  chooseFile: () => ipcRenderer.invoke('assets:choose'),
  chooseFolder: () => ipcRenderer.invoke('assets:chooseFolder'),
  setZoom: (z) => webFrame.setZoomFactor(Math.max(0.5, Math.min(2.5, Number(z) || 1))),
  resetAssets: () => ipcRenderer.invoke('assets:reset'),
  onProgress: (cb) => ipcRenderer.on('assets:progress', (_e, p) => cb(p)),
  saveText: (name, text) => ipcRenderer.invoke('file:save', name, text),
  saveTextFile: (name, text) => ipcRenderer.invoke('file:saveTxt', name, text),
  openText: () => ipcRenderer.invoke('file:open'),
  savePng: (name, dataUrl) => ipcRenderer.invoke('file:savePng', name, dataUrl),
  sendToMinecraft: (name, text, file) => ipcRenderer.invoke('mc:send', name, text, file),
  mcBuilds: {
    list: () => ipcRenderer.invoke('mcb:list'),
    get: (file) => ipcRenderer.invoke('mcb:get', file),
  },
  templates: {
    index: () => ipcRenderer.invoke('tpl:index'),
    get: (id) => ipcRenderer.invoke('tpl:get', id),
    thumb: (id) => `templates/thumbs/${id}.jpg`,
  },
  userBuilds: {
    list: () => ipcRenderer.invoke('ub:list'),
    get: (id) => ipcRenderer.invoke('ub:get', id),
    save: (id, text) => ipcRenderer.invoke('ub:save', id, text),
    del: (id) => ipcRenderer.invoke('ub:del', id),
  },
});
