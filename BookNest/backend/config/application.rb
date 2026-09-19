require_relative "boot"
require "rails/all"

Bundler.require(*Rails.groups)

module Booknest
  class Application < Rails::Application
    config.load_defaults 7.1
    config.autoload_lib(ignore: %w[assets tasks])
    # "Kyiv" isn't in ActiveSupport::TimeZone::MAPPING on every activesupport
    # version (the key used to be called "Kiev") — to avoid depending on a
    # specific minor gem version, we use UTC, which is guaranteed valid.
    config.time_zone = "UTC"
    # :uk here is only ever used as a *content* localization flag
    # (Book#localized / ?locale=uk param) — it is NOT a Rails i18n
    # translation locale, and there is no config/locales/uk.yml. Without
    # explicitly listing it as available, the moment I18n needs to render
    # any translated string while I18n.locale is :uk (e.g. building a
    # validation error message via ActiveRecord::RecordInvalid) it raises
    # I18n::InvalidLocale, turning what should be a normal 422 into an
    # unhandled 500. Registering it here is enough to stop that crash;
    # untranslated keys just fall back to the default en strings.
    config.i18n.available_locales = [:en, :uk]
    config.i18n.default_locale = :uk
  end
end
