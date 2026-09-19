module ApplicationHelper
  # URL of the React storefront (frontend-shop) — this is where sign-in and
  # registration now happen; Spring Boot is the identity source of truth,
  # Rails' own /session and /registrations routes are a legacy fallback.
  def shop_frontend_url
    ENV.fetch("SHOP_FRONTEND_URL", "http://localhost:5174")
  end
end
