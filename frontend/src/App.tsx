import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import './App.css'

type AuthMode = 'login' | 'signup'

type AuthForm = {
  email: string
  password: string
  nickname: string
}

type FieldError = {
  field: string
  message: string
}

type ApiErrorResponse = {
  code: string
  message: string
  fieldErrors: FieldError[]
}

type CurrentUserResponse = {
  id: number
  email: string
  nickname: string
  role: 'USER' | 'ADMIN'
  status: 'ACTIVE' | 'INACTIVE' | 'DELETED'
}

type LoginResponse = {
  tokenType: 'Bearer'
  accessToken: string
  expiresAt: string
}

type SignupResponse = {
  id: number
  email: string
  nickname: string
}

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api').replace(/\/$/, '')
const TOKEN_STORAGE_KEY = 'planmate.accessToken'
const TOKEN_EXPIRES_AT_STORAGE_KEY = 'planmate.accessTokenExpiresAt'

function App() {
  const [mode, setMode] = useState<AuthMode>('login')
  const [form, setForm] = useState<AuthForm>({
    email: '',
    password: '',
    nickname: '',
  })
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_STORAGE_KEY) ?? '')
  const [tokenExpiresAt, setTokenExpiresAt] = useState(
    () => localStorage.getItem(TOKEN_EXPIRES_AT_STORAGE_KEY) ?? '',
  )
  const [currentUser, setCurrentUser] = useState<CurrentUserResponse | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isLoadingUser, setIsLoadingUser] = useState(false)
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')

  const submitLabel = useMemo(() => {
    if (isSubmitting) {
      return mode === 'login' ? '로그인 중' : '가입 중'
    }

    return mode === 'login' ? '로그인' : '회원가입'
  }, [isSubmitting, mode])

  useEffect(() => {
    let ignore = false

    if (!token) {
      return undefined
    }

    async function syncCurrentUser() {
      setIsLoadingUser(true)
      setError('')

      try {
        const user = await request<CurrentUserResponse>('/auth/me', {}, token)
        if (!ignore) {
          setCurrentUser(user)
        }
      } catch (caughtError) {
        if (!ignore) {
          localStorage.removeItem(TOKEN_STORAGE_KEY)
          localStorage.removeItem(TOKEN_EXPIRES_AT_STORAGE_KEY)
          setToken('')
          setTokenExpiresAt('')
          setCurrentUser(null)
          setError(formatError(caughtError))
        }
      } finally {
        if (!ignore) {
          setIsLoadingUser(false)
        }
      }
    }

    void syncCurrentUser()

    return () => {
      ignore = true
    }
  }, [token])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setIsSubmitting(true)
    setNotice('')
    setError('')

    try {
      if (mode === 'signup') {
        const signedUpUser = await request<SignupResponse>('/auth/signup', {
          method: 'POST',
          body: JSON.stringify({
            email: form.email,
            password: form.password,
            nickname: form.nickname,
          }),
        })

        setMode('login')
        setForm((current) => ({
          ...current,
          email: signedUpUser.email,
          nickname: signedUpUser.nickname,
        }))
        setNotice('회원가입이 완료되었습니다. 로그인해 주세요.')
        return
      }

      const loginResponse = await request<LoginResponse>('/auth/login', {
        method: 'POST',
        body: JSON.stringify({
          email: form.email,
          password: form.password,
        }),
      })

      localStorage.setItem(TOKEN_STORAGE_KEY, loginResponse.accessToken)
      localStorage.setItem(TOKEN_EXPIRES_AT_STORAGE_KEY, loginResponse.expiresAt)
      setToken(loginResponse.accessToken)
      setTokenExpiresAt(loginResponse.expiresAt)
      setNotice('로그인되었습니다.')
    } catch (caughtError) {
      setError(formatError(caughtError))
    } finally {
      setIsSubmitting(false)
    }
  }

  async function handleLogout() {
    setNotice('')
    setError('')

    try {
      if (token) {
        await request<void>('/auth/logout', { method: 'POST' }, token)
      }
    } catch (caughtError) {
      setError(formatError(caughtError))
    } finally {
      clearSession()
      setNotice('로그아웃되었습니다.')
    }
  }

  function updateForm(field: keyof AuthForm, value: string) {
    setForm((current) => ({
      ...current,
      [field]: value,
    }))
  }

  function changeMode(nextMode: AuthMode) {
    setMode(nextMode)
    setNotice('')
    setError('')
  }

  function clearSession() {
    localStorage.removeItem(TOKEN_STORAGE_KEY)
    localStorage.removeItem(TOKEN_EXPIRES_AT_STORAGE_KEY)
    setToken('')
    setTokenExpiresAt('')
    setCurrentUser(null)
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <div>
          <p className="brand">PlanMate</p>
          <h1>인증</h1>
        </div>
        <span className={currentUser ? 'session-badge active' : 'session-badge'}>
          {currentUser ? '로그인됨' : '로그아웃'}
        </span>
      </header>

      <section className="auth-layout" aria-label="인증 화면">
        <section className="auth-panel" aria-labelledby="auth-title">
          <div className="mode-tabs" role="tablist" aria-label="인증 모드">
            <button
              type="button"
              className={mode === 'login' ? 'selected' : ''}
              onClick={() => changeMode('login')}
            >
              로그인
            </button>
            <button
              type="button"
              className={mode === 'signup' ? 'selected' : ''}
              onClick={() => changeMode('signup')}
            >
              회원가입
            </button>
          </div>

          <h2 id="auth-title">{mode === 'login' ? '계정 로그인' : '새 계정 생성'}</h2>

          <form className="auth-form" onSubmit={handleSubmit}>
            <label>
              이메일
              <input
                type="email"
                value={form.email}
                onChange={(event) => updateForm('email', event.target.value)}
                autoComplete="email"
                required
              />
            </label>

            <label>
              비밀번호
              <input
                type="password"
                value={form.password}
                onChange={(event) => updateForm('password', event.target.value)}
                autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                minLength={8}
                maxLength={72}
                required
              />
            </label>

            {mode === 'signup' && (
              <label>
                닉네임
                <input
                  type="text"
                  value={form.nickname}
                  onChange={(event) => updateForm('nickname', event.target.value)}
                  autoComplete="nickname"
                  minLength={2}
                  maxLength={30}
                  required
                />
              </label>
            )}

            <button className="primary-action" type="submit" disabled={isSubmitting}>
              {submitLabel}
            </button>
          </form>

          {notice && <p className="notice success">{notice}</p>}
          {error && <p className="notice error">{error}</p>}
        </section>

        <section className="account-panel" aria-labelledby="account-title">
          <div className="panel-header">
            <div>
              <p className="section-label">Session</p>
              <h2 id="account-title">현재 사용자</h2>
            </div>
            {currentUser && (
              <button className="secondary-action" type="button" onClick={handleLogout}>
                로그아웃
              </button>
            )}
          </div>

          {isLoadingUser && <p className="empty-state">사용자 정보를 불러오는 중입니다.</p>}

          {!isLoadingUser && currentUser && (
            <dl className="account-details">
              <div>
                <dt>사용자 ID</dt>
                <dd>{currentUser.id}</dd>
              </div>
              <div>
                <dt>이메일</dt>
                <dd>{currentUser.email}</dd>
              </div>
              <div>
                <dt>닉네임</dt>
                <dd>{currentUser.nickname}</dd>
              </div>
              <div>
                <dt>권한</dt>
                <dd>{currentUser.role}</dd>
              </div>
              <div>
                <dt>상태</dt>
                <dd>{currentUser.status}</dd>
              </div>
              <div>
                <dt>만료 시각</dt>
                <dd>{formatDateTime(tokenExpiresAt)}</dd>
              </div>
            </dl>
          )}

          {!isLoadingUser && !currentUser && (
            <p className="empty-state">로그인하면 계정 정보가 표시됩니다.</p>
          )}
        </section>
      </section>
    </main>
  )
}

async function request<T>(path: string, options: RequestInit = {}, accessToken?: string): Promise<T> {
  const headers = new Headers(options.headers)

  if (options.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`)
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers,
  })

  if (!response.ok) {
    throw await readApiError(response)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json() as Promise<T>
}

async function readApiError(response: Response): Promise<ApiErrorResponse> {
  try {
    return await response.json()
  } catch {
    return {
      code: 'REQUEST_ERROR',
      message: `요청 처리에 실패했습니다. (${response.status})`,
      fieldErrors: [],
    }
  }
}

function formatError(error: unknown): string {
  if (isApiErrorResponse(error)) {
    if (error.fieldErrors.length > 0) {
      return error.fieldErrors.map((fieldError) => `${fieldError.field}: ${fieldError.message}`).join(', ')
    }

    return error.message
  }

  if (error instanceof Error) {
    return error.message
  }

  return '요청 처리에 실패했습니다.'
}

function isApiErrorResponse(error: unknown): error is ApiErrorResponse {
  if (typeof error !== 'object' || error === null) {
    return false
  }

  return 'code' in error && 'message' in error && 'fieldErrors' in error
}

function formatDateTime(value: string): string {
  if (!value) {
    return '-'
  }

  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

export default App
