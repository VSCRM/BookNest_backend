# == SavedBook ===========================================================
# Join row for a shopper's wishlist ("saved books"). One row per
# (user, book) pair — the unique index on [user_id, book_id] means saving
# the same book twice is a no-op (find_or_create_by), matching how
# frontend-shop's useBookActions already de-duplicates on the client.
class SavedBook < ApplicationRecord
  belongs_to :user
  belongs_to :book
end
