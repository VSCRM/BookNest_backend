Rails.application.configure do
  config.enable_reloading = false
  config.eager_load = false
  config.action_controller.perform_caching = false
  config.active_support.deprecation = :stderr
end
