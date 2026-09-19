Rails.application.configure do
  config.enable_reloading = false
  config.eager_load = true
  config.consider_all_requests_local = false
  config.action_controller.perform_caching = true
  config.active_storage.service = :local
  config.log_level = :info
  config.force_ssl = false # local demo deployment, no TLS proxy in front

  # This is an educational local MVP, not a real production deployment:
  # there's intentionally no separate `assets:precompile` step in the
  # Dockerfile, so Sprockets is allowed to compile assets on the fly even
  # in production mode — otherwise the page fails with
  # Sprockets::Rails::Helper::AssetNotPrecompiledError.
  # For a real deployment, remove this and add
  # `RUN bin/rails assets:precompile` to the Dockerfile, along with a real
  # SECRET_KEY_BASE/master.key.
  config.assets.compile = true
end
