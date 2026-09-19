class ApplicationController < ActionController::Base
  helper_method :current_user, :current_cart

  private

  # A guest gets an "implicit" cart keyed by session_id, so adding to cart
  # works without an account (SRS role "Guest"). When that guest later
  # logs in or registers, `merge_guest_cart_into!` folds whatever they
  # already added into their real account cart — otherwise the guest cart
  # (tied to session_token) and the user's cart (tied to user_id) are two
  # separate rows and the guest's items silently vanish on login.
  def current_cart
    @current_cart ||= if current_user
                         cart = current_user.cart || current_user.create_cart!
                         merge_guest_cart_into!(cart) if session[:cart_token].present?
                         cart
                       else
                         token = session[:cart_token] ||= SecureRandom.hex(12)
                         Cart.find_or_create_by!(session_token: token)
                       end
  end

  # Folds the anonymous, session_token-keyed cart (if any) into the
  # freshly-identified user's cart, summing quantities for books already
  # present in both, then discards the guest cart and its token so this
  # only ever runs once per login.
  def merge_guest_cart_into!(user_cart)
    guest_cart = Cart.find_by(session_token: session[:cart_token])
    return if guest_cart.nil? || guest_cart.id == user_cart.id

    ActiveRecord::Base.transaction do
      guest_cart.cart_items.each do |item|
        existing = user_cart.cart_items.find_by(book_id: item.book_id)
        if existing
          existing.update!(quantity: existing.quantity + item.quantity)
          item.destroy
        else
          item.update!(cart_id: user_cart.id)
        end
      end
      guest_cart.destroy
    end
  ensure
    session.delete(:cart_token)
  end

  # Identity now lives in the Spring Boot auth-service. The primary path
  # is the `booknest_jwt` cookie it issues; a valid token is enough to
  # find-or-create the matching local shadow User (see User model comment)
  # and keep its role in sync with the JWT's "role" claim. Falls back to
  # the legacy Rails session (used only by the handful of pre-existing/
  # seeded accounts, e.g. the admin) when no JWT cookie is present.
  def current_user
    @current_user ||= user_from_jwt || user_from_session
  end

  def user_from_jwt
    claims = JwtAuthenticator.verify(cookies[JwtAuthenticator::ACCESS_COOKIE_NAME])
    return nil unless claims&.email

    email = claims.email.downcase
    name  = claims.name.presence || email
    role  = claims.role == "ADMIN" ? "admin" : "customer"

    # The frontend fires several requests in parallel right after login
    # (cart, saved books, orders), and each one lands here on its own
    # request/thread. On a brand-new account, more than one of them can
    # find no existing row and race to create it. Rather than each doing
    # find_or_initialize_by + save! (where the loser hits the uniqueness
    # validation and blows up with RecordInvalid), the loser here just
    # re-fetches the row the winner already committed.
    user = User.find_by(email: email)
    user ||= begin
      # Mirrors auth-service's UserService#randomPassword: a brand-new
      # shadow row must never be persisted with password_digest = nil.
      # This account is never logged into locally (identity lives in the
      # JWT/auth-service, has_secure_password validations:false already
      # allows a passwordless record), but leaving the column null is an
      # unnecessary footgun — a future bug or a bcrypt lib that treats
      # nil/blank as "no password required" could turn it into an
      # authentication bypass. A real, unguessable bcrypt hash of random
      # bytes costs nothing and closes that off, with no behavior change
      # for anyone.
      User.create!(email: email, name: name, role: role, password: SecureRandom.hex(32))
    rescue ActiveRecord::RecordInvalid, ActiveRecord::RecordNotUnique
      User.find_by!(email: email)
    end

    user.update!(name: name, role: role) if user.name != name || user.role != role
    user
  end

  def user_from_session
    User.find_by(id: session[:user_id])
  end
end
