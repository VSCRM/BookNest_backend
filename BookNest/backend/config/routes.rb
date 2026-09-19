Rails.application.routes.draw do
  # ── Root ─────────────────────────────────────────────────────────
  # The old catalog UI used to live at "/" (root -> books#index). It has
  # been moved behind the hidden /rubyback prefix below, so "/" itself is
  # intentionally left undefined here and returns Rails' normal 404 for an
  # unmatched route. Nothing is linked to "/" anymore — the real storefront
  # is the separate frontend-shop app (see docker-compose.yml).

  # ── Fun interactive welcome page ─────────────────────────────────
  # Only reachable by typing /welcome directly — not linked from any nav.
  get "welcome", to: "welcome#index"

  # ── JSON API (used by the React storefront) ──
  namespace :api do
    namespace :v1 do
      resources :books, only: %i[index show]

      # Admin catalog management (BookNest React admin panel).
      namespace :admin do
        resources :books, only: %i[index create update destroy]
      end

      # Saved-books ("wishlist") JSON API for the React storefront.
      # `username` is an e-mail (e.g. "admin@booknest.local"): without the
      # constraint below, Rails treats everything after the *last* dot as a
      # :format ("...booknest.local" -> username "...booknest", format
      # "local"), which doesn't match any registered format and 404s. The
      # regex accepts anything except a literal "/", dots included.
      user_email = { username: /[^\/]+/ }
      get    "users/:username/saved",          to: "users#saved_index",   as: :api_saved_books,        constraints: user_email
      post   "users/:username/saved",          to: "users#saved_create",  as: :api_saved_book_create,  constraints: user_email
      delete "users/:username/saved/:book_id", to: "users#saved_destroy", as: :api_saved_book_destroy, constraints: user_email

      # JSON cart/orders API for the React storefront (frontend-shop).
      get    "cart",                to: "carts#show",        as: :api_cart
      post   "cart/items",          to: "carts#add_item",    as: :api_add_cart_item
      patch  "cart/items/:book_id", to: "carts#update_item", as: :api_update_cart_item
      delete "cart/items/:book_id", to: "carts#remove_item", as: :api_remove_cart_item

      resources :orders, only: %i[index show create]

      # Ruby code-execution API for frontend-playground (BookNest labs 5-7:
      # strings/arithmetic, arrays, classes/OOP). This is the actual "Ruby
      # backend" the playground talks to — not to be confused with the old
      # /rubyback HTML site above, which has been removed entirely.
      post "playground/execute", to: "playground#execute"
      post "playground/lint",    to: "playground#lint"
    end
  end

  # ── Swagger / OpenAPI docs ────────────────────────────────────────
  # Strictly at /api (no other alias) — same static Swagger UI as before,
  # just moved off of /docs. Does not collide with the JSON /api/v1/*
  # namespace above, since this only matches the exact "/api" path.
  get "api", to: redirect("/docs/index.html")

  get "up" => "rails/health#show", as: :rails_health_check
end
