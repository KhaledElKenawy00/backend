/*
 * Mints a demo HS256 JWT identical in shape to user-service's tokens (subject = email, plus a
 * `userId` claim the gateway/Colyseus read). Signed with the shared JWT_SECRET so the gateway and
 * the Colyseus fork accept it. Prints just the token to stdout.
 *
 *   JWT_SECRET=... DEMO_USER_ID=1 DEMO_EMAIL=demo@office.dev node mint-jwt.mjs
 */
import crypto from 'crypto'

const secret =
  process.env.JWT_SECRET || 'your-very-strong-secret-key-must-be-at-least-32-characters-long'
const userId = Number(process.env.DEMO_USER_ID || 1)
const email = process.env.DEMO_EMAIL || 'demo@office.dev'

const now = Math.floor(Date.now() / 1000)
const b64 = (obj) => Buffer.from(JSON.stringify(obj)).toString('base64url')

const header = b64({ alg: 'HS256', typ: 'JWT' })
const payload = b64({ sub: email, userId, iat: now, exp: now + 24 * 60 * 60 })
const signature = crypto.createHmac('sha256', secret).update(`${header}.${payload}`).digest('base64url')

process.stdout.write(`${header}.${payload}.${signature}`)
