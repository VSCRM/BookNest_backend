# == JwtAuthenticator ======================================================
# Verifies JWTs issued by the Spring Boot auth-service (HS256, shared
# secret via ENV["JWT_SECRET"] — must match the auth-service's own
# booknest.jwt.secret). This is the only place in the Rails app that knows
# how to read a BookNest access token; ApplicationController#current_user
# delegates here instead of decoding tokens itself (SRP).
class JwtAuthenticator
  Claims = Struct.new(:email, :name, :role, keyword_init: true)

  ALGORITHM = "HS256"
  # Must match Spring's CookieUtil.ACCESS_COOKIE exactly.
  ACCESS_COOKIE_NAME = "booknest_jwt"

  class << self
    # Returns a Claims struct for a valid, non-expired access token,
    # or nil for a missing/invalid/expired/wrong-type token. Never raises.
    def verify(token)
      return nil if token.blank?

      payload, = JWT.decode(token, secret, true, algorithm: ALGORITHM)
      return nil unless payload["typ"] == "access"

      Claims.new(email: payload["sub"], name: payload["name"], role: payload["role"])
    rescue JWT::DecodeError, JWT::ExpiredSignature
      nil
    end

    private

    def secret
      ENV.fetch("JWT_SECRET", "change-me-to-a-real-32-byte-minimum-secret-value")
    end
  end
end
