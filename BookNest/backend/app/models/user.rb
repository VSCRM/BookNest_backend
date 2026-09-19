# == User ===============================================================
# Identity now lives in the Spring Boot auth-service — Rails no longer
# performs its own registration/login. This local `users` table is a thin
# shadow/cache keyed by email, created on first sight of a valid JWT
# (see JwtAuthenticator + ApplicationController#current_user), purely so
# carts and orders have a local foreign key to attach to.
#
# has_secure_password(validations: false): Rails-issued passwords are no
# longer the primary credential path, so a JWT-authenticated shadow user
# (no password at all) must still be a valid, persistable record. The
# password/bcrypt machinery is kept only for the small number of
# pre-existing/seeded accounts (e.g. the admin) that still log in locally.
class User < ApplicationRecord
  has_secure_password validations: false

  has_one :cart, dependent: :destroy
  has_many :orders, dependent: :nullify
  has_many :saved_books, dependent: :destroy
  has_many :wishlisted_books, through: :saved_books, source: :book

  validates :name,  presence: true
  validates :email, presence: true, uniqueness: { case_sensitive: false },
                     format: { with: URI::MailTo::EMAIL_REGEXP }
  validates :password, length: { minimum: 6 }, allow_nil: true

  before_save { self.email = email.downcase }

  def admin?
    role == "admin"
  end
end
