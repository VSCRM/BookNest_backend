# Allows the frontends (local Vite dev server + the deployed GitHub Pages
# build) to call the Rails API with credentials (cookies).
#
# Configurable via CORS_ALLOWED_ORIGINS (comma-separated) so a deployed
# frontend origin doesn't require a code change + rebuild — just set the
# env var. Falls back to the local dev + GitHub Pages origins BookNest
# actually uses if unset.
allowed_origins = ENV.fetch(
  "CORS_ALLOWED_ORIGINS",
  "http://localhost:5174,http://127.0.0.1:5174,http://localhost:5173,http://127.0.0.1:5173," \
  "https://vscrm.github.io"
).split(",").map(&:strip).reject(&:empty?)

Rails.application.config.middleware.insert_before 0, Rack::Cors do
  allow do
    origins(*allowed_origins)
    resource "/api/*", headers: :any, methods: %i[get post put patch delete options],
                        credentials: true
  end
end
