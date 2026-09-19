# == Cart ================================================================
# A cart is tied to either a user_id (logged-in customer) or a
# session_token (guest) — this way a guest doesn't lose their cart
# between requests.
class Cart < ApplicationRecord
  belongs_to :user, optional: true
  has_many :cart_items, dependent: :destroy
  has_many :books, through: :cart_items

  def total_price
    cart_items.includes(:book).sum { |i| i.book.price * i.quantity }
  end

  def total_items
    cart_items.sum(:quantity)
  end
end
