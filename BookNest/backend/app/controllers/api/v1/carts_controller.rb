module Api
  module V1
    # JSON cart API for the React storefront (frontend-shop). Inherits
    # ApplicationController (not ActionController::API) specifically to
    # reuse current_user/current_cart, which depend on cookies/session
    # helpers that ActionController::API does not include.
    class CartsController < ApplicationController
      skip_before_action :verify_authenticity_token, raise: false

      def show
        render json: serialize_cart(current_cart)
      end

      def add_item
        book = Book.find(params[:book_id])
        CartService.new(current_cart).add(book, (params[:quantity] || 1).to_i)
        render json: serialize_cart(current_cart)
      rescue CartService::OutOfStockError => e
        render json: {error: e.message}, status: :unprocessable_entity
      end

      def update_item
        book = Book.find(params[:book_id])
        CartService.new(current_cart).update_quantity(book, params[:quantity])
        render json: serialize_cart(current_cart)
      end

      def remove_item
        book = Book.find(params[:book_id])
        CartService.new(current_cart).remove(book)
        render json: serialize_cart(current_cart)
      end

      private

      def serialize_cart(cart)
        items = cart.cart_items.includes(:book).map do |item|
          {
            book_id: item.book_id,
            title: item.book.title,
            author: item.book.author,
            price: item.book.price,
            cover_image_url: item.book.cover_image_url,
            stock: item.book.stock,
            quantity: item.quantity,
            subtotal: item.subtotal
          }
        end

        {items: items, total_price: cart.total_price, total_items: cart.total_items}
      end
    end
  end
end
