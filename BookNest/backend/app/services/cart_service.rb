# == CartService ==========================================================
# Service Object (SRP — pulls business logic out of the controller, SOLID).
# Encapsulates cart operations so CartsController stays "thin".
class CartService
  class OutOfStockError < StandardError; end

  def initialize(cart)
    @cart = cart
  end

  def add(book, quantity = 1)
    raise OutOfStockError, "Книги немає в наявності" unless book.in_stock?

    item = @cart.cart_items.find_or_initialize_by(book: book)
    item.quantity = item.new_record? ? quantity : item.quantity + quantity
    item.save!
    item
  end

  def update_quantity(book, quantity)
    item = @cart.cart_items.find_by!(book: book)
    if quantity.to_i <= 0
      item.destroy
    else
      item.update!(quantity: quantity)
    end
  end

  def remove(book)
    @cart.cart_items.find_by(book: book)&.destroy
  end

  def checkout!(user: nil)
    raise ActiveRecord::RecordInvalid.new(Order.new) if @cart.cart_items.empty?

    order = nil
    ActiveRecord::Base.transaction do
      order = Order.create!(user: user)
      @cart.cart_items.includes(:book).each do |item|
        order.order_items.create!(book: item.book, quantity: item.quantity, unit_price: item.book.price)
      end
      @cart.cart_items.destroy_all
    end
    order
  end
end
