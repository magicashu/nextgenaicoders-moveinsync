import { createContext, useContext, type ReactNode } from 'react'
import { api as defaultApi, type CopilotApi } from '../core/api'

const ApiContext = createContext<CopilotApi>(defaultApi)

export function ApiProvider({ api, children }: { api?: CopilotApi; children: ReactNode }) {
  return <ApiContext.Provider value={api ?? defaultApi}>{children}</ApiContext.Provider>
}

export function useApi(): CopilotApi {
  return useContext(ApiContext)
}
