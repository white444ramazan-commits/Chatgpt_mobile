import { readFile, writeFile } from "node:fs/promises";

const accountId = process.env.CLOUDFLARE_ACCOUNT_ID;
const apiToken = process.env.CLOUDFLARE_API_TOKEN;
if (!accountId || !apiToken) throw new Error("CLOUDFLARE_ACCOUNT_ID and CLOUDFLARE_API_TOKEN are required.");

const base = "https://api.cloudflare.com/client/v4";
async function cf(path, init = {}) {
  const response = await fetch(base + path, {
    ...init,
    headers: {
      authorization: `Bearer ${apiToken}`,
      "content-type": "application/json",
      ...(init.headers ?? {})
    }
  });
  const data = await response.json();
  if (!response.ok || !data.success) {
    throw new Error(`Cloudflare API ${response.status}: ${JSON.stringify(data.errors ?? data)}`);
  }
  return data.result;
}

const d1List = await cf(`/accounts/${accountId}/d1/database?per_page=100`);
let d1 = d1List.find((item) => item.name === "gorilla-control-db");
if (!d1) {
  d1 = await cf(`/accounts/${accountId}/d1/database`, {
    method: "POST",
    body: JSON.stringify({ name: "gorilla-control-db" })
  });
}

const kvList = await cf(`/accounts/${accountId}/storage/kv/namespaces?per_page=100`);
let kv = kvList.find((item) => item.title === "GORILLA_CONTROL_OAUTH");
if (!kv) {
  kv = await cf(`/accounts/${accountId}/storage/kv/namespaces`, {
    method: "POST",
    body: JSON.stringify({ title: "GORILLA_CONTROL_OAUTH" })
  });
}

if (!d1?.uuid || !kv?.id) throw new Error("Cloudflare did not return the D1 or KV namespace identifier.");
const configPath = new URL("../wrangler.jsonc", import.meta.url);
const config = JSON.parse(await readFile(configPath, "utf8"));
config.account_id = accountId;
config.d1_databases[0].database_id = d1.uuid;
config.kv_namespaces[0].id = kv.id;
await writeFile(configPath, JSON.stringify(config, null, 2) + "\n");
console.log("Provisioned/reused Cloudflare D1 and KV resources.");
