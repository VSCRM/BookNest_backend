class CartItem < ApplicationRecord
  belongs_to :cart
  belongs_to :book

  validates :quantity, numericality: { only_integer: true, greater_than: 0 }

  def subtotal
    book.price * quantity
  end
end
