# Evidence checklist

This folder holds the screenshots that the main [README](../README.md#9-evidence) embeds.
Drop each file in here with the exact name shown below.

| # | Filename | What it should show |
|---|---|---|
| 1 | `local-run.png` | The server running locally (console log) next to the browser showing `index.html` |
| 2 | `static-resource.png` | Browser DevTools Network tab: a `200` request for `/app.js` or `/images/logo.png` with the right `Content-Type` |
| 3 | `endpoint-hello.png` | `GET /hello?name=...` returning the dynamic greeting |
| 4 | `endpoint-pi.png` | `GET /pi` returning `Math.PI` |
| 5 | `response-404.png` | `GET /unknown` (or any unregistered path) returning `404 Not Found` |
| 6 | `env-vars.png` | The environment variables configured for the run/deployment (`PORT`, `APP_ENV`, `GREETING_PREFIX`), with no secrets visible |
| 7 | `shutdown-dev.png` | `GET /shutdown` stopping the server locally with `APP_ENV=development` |
| 8 | `shutdown-prod-404.png` | `GET /shutdown` returning `404` when `APP_ENV=production` |
| 9 | `cloud-deployed.png` | The application loaded from its public cloud URL: page, script, and images all working |
| 10 | `cloud-endpoints.png` | Both REST endpoints (`/hello`, `/pi`) responding from the public cloud URL |

A few tips:

- Crop out anything that identifies the instance/account (public IP, account ID, ARNs) unless the
  lab specifically asks for it.
- Keep the filenames exactly as listed above. GitHub's rendering is case-sensitive.
