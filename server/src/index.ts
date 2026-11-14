import { WorkerEntrypoint, env } from "cloudflare:workers";
import {
  AuthorizationError,
  OAuthProvider,
  type OAuthHelpers
} from "@cloudflare/workers-oauth-provider";
import { createMcpHandler } from "agents/mcp/server";
import { McpServer } from "@modelcontextprotocol/server";
import { z } from "zod";

interface Env {
  DB: D1Database;
  OAUTH_KV: KVNamespace;
  OAUTH_PROVIDER: OAuthHelpers;
  DEVICE_PAIRING_CODE: string;
  OWNER_PASSPHRASE: string;
  MCP_RESOURCE: string;
}

interface AuthProps {
  userId: string;
  scopes: string[];
}

const DEVICE_ID = "primary";
const COMMAND_TTL_MS = 90_000;
const DEVICE_ONLINE_MS = 45_000;
const APP_NAME = "GORILLA CONTROL";

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" }
  });
}

function html(source: string, status = 200): Response {
  return new Response(source, {
    status,
    headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store", "x-content-type-options": "nosniff" }
  });
}

function escapeHtml(value: unknown): string {
  return String(value ?? "").replace(/[&<>"']/g, (ch) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
  })[ch] ?? ch);
}

function sameSecret(a: string, b: string): boolean {
  let diff = a.length ^ b.length;
  const size = Math.max(a.length, b.length);
  for (let i = 0; i < size; i++) {
    diff |= (a.charCodeAt(i % Math.max(1, a.length)) || 0) ^
            (b.charCodeAt(i % Math.max(1, b.length)) || 0);
  }
  return diff === 0;
}

async function sha256Hex(value: string): Promise<string> {
  const bytes = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest)).map((b) => b.toString(16).padStart(2, "0")).join("");
}

function randomToken(): string {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  let binary = "";
  bytes.forEach((byte) => { binary += String.fromCharCode(byte); });
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

async function readJson(request: Request): Promise<Record<string, unknown> | null> {
  const length = Number(request.headers.get("content-length") ?? "0");
  if (length > 24_000) return null;
  try {
    const value = await request.json();
    return value && typeof value === "object" && !Array.isArray(value)
      ? value as Record<string, unknown> : null;
  } catch {
    return null;
  }
}

function bearer(request: Request): string | null {
  const value = request.headers.get("authorization") ?? "";
  const match = /^Bearer\s+([A-Za-z0-9_\-]+)$/i.exec(value);
  return match?.[1] ?? null;
}

async function authenticatedDevice(request: Request, env: Env): Promise<boolean> {
  const token = bearer(request);
  if (!token) return false;
  const digest = await sha256Hex(token);
  const row = await env.DB.prepare(
    "SELECT id FROM devices WHERE id = ? AND token_hash = ?"
  ).bind(DEVICE_ID, digest).first<{ id: string }>();
  return Boolean(row);
}

async function pairDevice(request: Request, env: Env): Promise<Response> {
  if (request.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  const body = await readJson(request);
  const submitted = typeof body?.pairingCode === "string" ? body.pairingCode : "";
  const name = typeof body?.deviceName === "string" ? body.deviceName.slice(0, 80) : "Android device";
  if (!env.DEVICE_PAIRING_CODE || !sameSecret(submitted, env.DEVICE_PAIRING_CODE)) {
    return json({ error: "invalid_pairing_code" }, 401);
  }

  const token = randomToken();
  const now = Date.now();
  await env.DB.prepare(
    `INSERT INTO devices (id, device_name, token_hash, created_at_ms, last_seen_ms)
     VALUES (?, ?, ?, ?, ?)
     ON CONFLICT(id) DO UPDATE SET
       device_name = excluded.device_name,
       token_hash = excluded.token_hash,
       created_at_ms = excluded.created_at_ms,
       last_seen_ms = excluded.last_seen_ms`
  ).bind(DEVICE_ID, name || "Android device", await sha256Hex(token), now, now).run();

  return json({ deviceId: DEVICE_ID, deviceToken: token, message: "Device paired. Store the token securely." });
}

async function deviceHeartbeat(request: Request, env: Env): Promise<Response> {
  if (request.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  if (!await authenticatedDevice(request, env)) return json({ error: "unauthorized" }, 401);
  const body = await readJson(request);
  if (!body) return json({ error: "invalid_json" }, 400);
  const width = Number.isInteger(body.screenWidth) ? Number(body.screenWidth) : null;
  const height = Number.isInteger(body.screenHeight) ? Number(body.screenHeight) : null;
  const root = body.rootAvailable === true ? 1 : body.rootAvailable === false ? 0 : null;

  await env.DB.prepare(
    `UPDATE devices SET
       last_seen_ms = ?, model = ?, android_version = ?, root_available = ?,
       screen_width = ?, screen_height = ?
     WHERE id = ?`
  ).bind(
    Date.now(),
    typeof body.model === "string" ? body.model.slice(0, 100) : null,
    typeof body.androidVersion === "string" ? body.androidVersion.slice(0, 40) : null,
    root, width, height, DEVICE_ID
  ).run();

  return json({ ok: true, serverTimeMs: Date.now() });
}

async function nextDeviceCommand(request: Request, env: Env): Promise<Response> {
  if (request.method !== "GET") return json({ error: "method_not_allowed" }, 405);
  if (!await authenticatedDevice(request, env)) return json({ error: "unauthorized" }, 401);
  const now = Date.now();

  await env.DB.prepare(
    "UPDATE commands SET status = 'expired', updated_at_ms = ? WHERE device_id = ? AND status IN ('queued', 'delivered') AND expires_at_ms <= ?"
  ).bind(now, DEVICE_ID, now).run();

  const command = await env.DB.prepare(
    `SELECT id, tool_name, payload_json, created_at_ms, expires_at_ms
     FROM commands
     WHERE device_id = ? AND status IN ('queued', 'delivered') AND expires_at_ms > ?
     ORDER BY created_at_ms ASC LIMIT 1`
  ).bind(DEVICE_ID, now).first<{
    id: string; tool_name: string; payload_json: string; created_at_ms: number; expires_at_ms: number;
  }>();

  if (!command) return json({ command: null });
  if (command) {
    await env.DB.prepare(
      "UPDATE commands SET status = 'delivered', updated_at_ms = ? WHERE id = ? AND status = 'queued'"
    ).bind(now, command.id).run();
  }

  return json({
    command: {
      id: command.id,
      name: command.tool_name,
      payload: JSON.parse(command.payload_json),
      createdAtMs: command.created_at_ms,
      expiresAtMs: command.expires_at_ms
    }
  });
}

async function submitCommandResult(request: Request, env: Env, commandId: string): Promise<Response> {
  if (request.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  if (!await authenticatedDevice(request, env)) return json({ error: "unauthorized" }, 401);
  const body = await readJson(request);
  if (!body) return json({ error: "invalid_json" }, 400);

  const status = body.status;
  if (!["completed", "rejected", "failed"].includes(String(status))) {
    return json({ error: "invalid_status" }, 400);
  }
  const result = JSON.stringify(body.result ?? {});
  if (result.length > 18_000) return json({ error: "result_too_large" }, 413);

  const updated = await env.DB.prepare(
    `UPDATE commands SET status = ?, result_json = ?, updated_at_ms = ?
     WHERE id = ? AND device_id = ? AND status IN ('queued', 'delivered') AND expires_at_ms > ?`
  ).bind(String(status), result, Date.now(), commandId, DEVICE_ID, Date.now()).run();

  if (!updated.meta.changes) return json({ error: "command_not_found_or_expired" }, 404);
  return json({ ok: true });
}

async function enqueue(db: D1Database, name: string, payload: Record<string, unknown>) {
  const device = await db.prepare("SELECT id FROM devices WHERE id = ?").bind(DEVICE_ID).first();
  if (!device) return {
    isError: true,
    content: [{ type: "text" as const, text: "Планшет ещё не подключён. Сначала спари GORILLA CONTROL с сервером из приложения." }]
  };

  const id = crypto.randomUUID();
  const now = Date.now();
  await db.prepare(
    `INSERT INTO commands (id, device_id, tool_name, payload_json, status, created_at_ms, updated_at_ms, expires_at_ms)
     VALUES (?, ?, ?, ?, 'queued', ?, ?, ?)`
  ).bind(id, DEVICE_ID, name, JSON.stringify(payload), now, now, now + COMMAND_TTL_MS).run();

  return {
    isError: false,
    content: [{
      type: "text" as const,
      text: JSON.stringify({
        commandId: id,
        status: "waiting_for_device_approval",
        message: "Запрос отправлен на планшет. Он НЕ выполнится, пока пользователь не нажмёт «Разрешить» в GORILLA CONTROL."
      })
    }]
  };
}

function createServer(db: D1Database, scopes: string[]) {
  const server = new McpServer({ name: "GORILLA CONTROL", version: "0.3.0" });
  const canRead = scopes.includes("mcp:read");
  const canControl = scopes.includes("mcp:control");

  server.registerTool(
    "get_device_status",
    { description: "Показывает статус спаренного Android-планшета GORILLA CONTROL.", inputSchema: {} },
    async (_args: Record<string, never>) => {
      if (!canRead) return { isError: true as const, content: [{ type: "text" as const, text: "Недостаточно прав: mcp:read." }] };
      const device = await db.prepare(
        "SELECT device_name, last_seen_ms, model, android_version, root_available, screen_width, screen_height FROM devices WHERE id = ?"
      ).bind(DEVICE_ID).first<{
        device_name: string; last_seen_ms: number | null; model: string | null; android_version: string | null;
        root_available: number | null; screen_width: number | null; screen_height: number | null;
      }>();
      if (!device) return { content: [{ type: "text" as const, text: "Планшет ещё не спарен." }] };
      const online = device.last_seen_ms !== null && Date.now() - device.last_seen_ms < DEVICE_ONLINE_MS;
      return { content: [{ type: "text" as const, text: JSON.stringify({
        device: device.device_name, online, lastSeenMs: device.last_seen_ms,
        model: device.model, androidVersion: device.android_version,
        rootAvailable: device.root_available === null ? null : device.root_available === 1,
        screen: device.screen_width && device.screen_height
          ? { width: device.screen_width, height: device.screen_height } : null
      }) }] };
    }
  );

  server.registerTool(
    "request_tap",
    {
      description: "Запрашивает нажатие по координатам. На планшете появится запрос; касание выполняется только после ручного подтверждения пользователя.",
      inputSchema: { x: z.number().int().min(0).max(12000), y: z.number().int().min(0).max(12000) }
    },
    async ({ x, y }) => {
      if (!canControl) return { isError: true as const, content: [{ type: "text" as const, text: "Требуется OAuth-разрешение mcp:control." }] };
      return enqueue(db, "tap", { x, y });
    }
  );

  server.registerTool(
    "request_swipe",
    {
      description: "Запрашивает свайп. На планшете появится запрос; жест выполнится только после ручного подтверждения пользователя.",
      inputSchema: {
        x1: z.number().int().min(0).max(12000),
        y1: z.number().int().min(0).max(12000),
        x2: z.number().int().min(0).max(12000),
        y2: z.number().int().min(0).max(12000),
        durationMs: z.number().int().min(80).max(2000).default(350)
      }
    },
    async ({ x1, y1, x2, y2, durationMs }) => {
      if (!canControl) return { isError: true as const, content: [{ type: "text" as const, text: "Требуется OAuth-разрешение mcp:control." }] };
      return enqueue(db, "swipe", { x1, y1, x2, y2, durationMs });
    }
  );

  server.registerTool(
    "request_launch_app",
    {
      description: "Запрашивает запуск приложения по Android package name. Запуск требует подтверждения на планшете.",
      inputSchema: { packageName: z.string().min(3).max(160).regex(/^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$/) }
    },
    async ({ packageName }) => {
      if (!canControl) return { isError: true as const, content: [{ type: "text" as const, text: "Требуется OAuth-разрешение mcp:control." }] };
      return enqueue(db, "launch_app", { packageName });
    }
  );

  server.registerTool(
    "get_command_result",
    { description: "Проверяет результат ранее отправленной команды.", inputSchema: { commandId: z.string().uuid() } },
    async ({ commandId }) => {
      if (!canRead) return { isError: true as const, content: [{ type: "text" as const, text: "Недостаточно прав: mcp:read." }] };
      const row = await db.prepare(
        "SELECT tool_name, status, result_json, created_at_ms, updated_at_ms FROM commands WHERE id = ? AND device_id = ?"
      ).bind(commandId, DEVICE_ID).first<{
        tool_name: string; status: string; result_json: string | null; created_at_ms: number; updated_at_ms: number;
      }>();
      if (!row) return { content: [{ type: "text" as const, text: "Команда не найдена." }] };
      return { content: [{ type: "text" as const, text: JSON.stringify({ ...row, result: row.result_json ? JSON.parse(row.result_json) : null }) }] };
    }
  );

  server.registerTool(
    "list_recent_commands",
    { description: "Показывает последние 10 запросов управления и их статусы.", inputSchema: {} },
    async () => {
      if (!canRead) return { isError: true as const, content: [{ type: "text" as const, text: "Недостаточно прав: mcp:read." }] };
      const rows = await db.prepare(
        "SELECT id, tool_name, status, created_at_ms, updated_at_ms FROM commands WHERE device_id = ? ORDER BY created_at_ms DESC LIMIT 10"
      ).bind(DEVICE_ID).all();
      return { content: [{ type: "text" as const, text: JSON.stringify(rows.results ?? []) }] };
    }
  );

  return server;
}

class McpApiHandler extends WorkerEntrypoint<Env, AuthProps> {
  fetch(request: Request): Promise<Response> {
    const scopes = this.ctx.props?.scopes ?? [];
    const handler = createMcpHandler(() => createServer(this.env.DB, scopes), { route: "/mcp" });
    return handler(request, this.env, this.ctx);
  }
}

const defaultHandler: ExportedHandler<Env> = {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (url.pathname === "/health" && request.method === "GET") {
      try {
        await env.DB.prepare("SELECT 1 AS ok").first();
        return json({ ok: true, service: APP_NAME, database: "connected" });
      } catch {
        return json({ ok: false, database: "unavailable" }, 503);
      }
    }

    if (url.pathname === "/device/pair") return pairDevice(request, env);
    if (url.pathname === "/device/heartbeat") return deviceHeartbeat(request, env);
    if (url.pathname === "/device/commands/next") return nextDeviceCommand(request, env);

    const resultMatch = /^\/device\/commands\/([0-9a-f-]{36})\/result$/i.exec(url.pathname);
    if (resultMatch) return submitCommandResult(request, env, resultMatch[1]);

    if (url.pathname !== "/authorize") return json({ error: "not_found" }, 404);

    let oauthRequest;
    try {
      oauthRequest = await env.OAUTH_PROVIDER.parseAuthRequest(request);
    } catch (error) {
      if (!(error instanceof AuthorizationError)) throw error;
      if (!error.redirectUri) return html("<h1>OAuth request rejected</h1><p>Invalid or unsupported authorization request.</p>", 400);
      const redirect = new URL(error.redirectUri);
      redirect.searchParams.set("error", error.code);
      redirect.searchParams.set("error_description", error.description);
      if (error.state) redirect.searchParams.set("state", error.state);
      if (error.issuer) redirect.searchParams.set("iss", error.issuer);
      return Response.redirect(redirect.toString(), 302);
    }

    const action = `/authorize${url.search}`;
    if (request.method === "GET") {
      const details = await env.OAUTH_PROVIDER.describeConsent(oauthRequest);
      const consent = await env.OAUTH_PROVIDER.beginConsent(oauthRequest);
      const scopeFields = details.scope
        .filter((scope) => scope !== "mcp:read")
        .map((scope) => {
          const checked = scope === "mcp:control" || scope === "offline_access" ? " checked" : "";
          return `<label><input type="checkbox" name="scope" value="${escapeHtml(scope)}"${checked} style="width:auto"> ${escapeHtml(scope)}</label>`;
        }).join("");
      const page = `<!doctype html><html lang="ru"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>GORILLA CONTROL authorization</title>
      <style>
      body{background:#090b10;color:#f4f4f5;font:16px system-ui;margin:0;padding:28px}
      main{max-width:540px;margin:6vh auto;background:#171c26;padding:28px;border-radius:20px}
      label{display:block;margin:16px 0}
      input[type=password]{padding:12px;border-radius:9px;border:1px solid #555;background:#090b10;color:white;width:100%;box-sizing:border-box}
      input[type=checkbox]{width:auto}
      button{background:#8b5cf6;color:white;border:0;padding:14px 20px;border-radius:10px;font-weight:700;width:100%;margin-top:10px}
      button.deny{background:#343844}.muted{color:#aeb6c4}.warning{color:#f5c56b}
      </style>
      <main><h1>GORILLA CONTROL</h1>
      <p>Приложение <strong>${escapeHtml(details.clientName)}</strong> запрашивает доступ к сопряжённому Android-планшету.</p>
      ${details.clientDomain ? `<p class="muted">Проверенный домен клиента: <strong>${escapeHtml(details.clientDomain)}</strong></p>` : '<p class="muted">Имя клиента предоставлено самим приложением и не проверено.</p>'}
      <p>OAuth-токен будет отправлен на: <strong>${escapeHtml(details.redirectHost)}</strong></p>
      ${details.redirectIsLoopback ? '<p class="warning">Внимание: адрес возврата ведёт в локальное приложение на этом устройстве.</p>' : ""}
      <p class="muted">Обязательное разрешение: mcp:read. Запросы управления всё равно требуют подтверждения на планшете.</p>
      <form method="post" action="${escapeHtml(action)}">
        <input type="hidden" name="handle" value="${escapeHtml(consent.handle)}">
        <input type="hidden" name="scope" value="mcp:read">
        ${scopeFields}
        <label>Пароль владельца<input type="password" name="passphrase" required autocomplete="current-password"></label>
        <button type="submit" name="decision" value="approve">Разрешить ChatGPT</button>
        <button class="deny" type="submit" name="decision" value="deny">Отклонить</button>
      </form>
      <p class="muted">Каждое нажатие, свайп или запуск приложения дополнительно подтверждается на самом планшете.</p>
      </main></html>`;
      consent.headers.set("Content-Type", "text/html; charset=utf-8");
      return new Response(page, { status: 200, headers: consent.headers });
    }

    if (request.method === "POST") {
      const form = await request.formData();
      const handle = String(form.get("handle") ?? "");
      if (form.get("decision") !== "approve") {
        const denied = await env.OAUTH_PROVIDER.denyConsent(request, handle);
        return new Response(null, { status: 302, headers: denied.headers });
      }

      const passphrase = String(form.get("passphrase") ?? "");
      if (!env.OWNER_PASSPHRASE || !sameSecret(passphrase, env.OWNER_PASSPHRASE)) {
        return html("<h1>Authorization failed</h1><p>Неверный пароль владельца. Вернись назад и повтори подтверждение.</p>", 401);
      }

      const scopes = form.getAll("scope").map(String);
      if (!scopes.includes("mcp:read")) scopes.push("mcp:read");
      const approved = await env.OAUTH_PROVIDER.approveConsent(request, handle, { scope: scopes });
      const { redirectTo } = await env.OAUTH_PROVIDER.completeAuthorization({
        request: approved.request,
        userId: "owner",
        metadata: { clientName: "GORILLA CONTROL owner" },
        scope: approved.request.scope,
        props: { userId: "owner", scopes: approved.request.scope }
      });
      approved.headers.set("Location", redirectTo);
      return new Response(null, { status: 302, headers: approved.headers });
    }
    return json({ error: "method_not_allowed" }, 405);
  }
};

export default new OAuthProvider<Env>({
  apiRoute: "/mcp",
  apiHandler: McpApiHandler,
  defaultHandler,
  authorizeEndpoint: "/authorize",
  tokenEndpoint: "/oauth/token",
  clientRegistrationEndpoint: "/oauth/register",
  resourceMetadata: {
    resource: (env as unknown as Env).MCP_RESOURCE,
    authorization_servers: [new URL((env as unknown as Env).MCP_RESOURCE).origin],
    scopes_supported: ["mcp:read", "mcp:control"],
    resource_name: APP_NAME
  },
  scopesSupported: ["mcp:read", "mcp:control", "offline_access"],
  requiredScopes: ["mcp:read"],
  clientIdMetadataDocumentEnabled: true
});
