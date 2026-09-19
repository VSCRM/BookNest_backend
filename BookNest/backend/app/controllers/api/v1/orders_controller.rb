module Api
  module V1
    # JSON orders API for the React storefront (frontend-shop): checkout
    # and order-history/tracking. Inherits ApplicationController for the
    # same reason as CartsController — current_user/current_cart need
    # cookies/session, which ActionController::API does not provide.
    class OrdersController < ApplicationController
      skip_before_action :verify_authenticity_token, raise: false
      # `show` used to be reachable with no login at all, and even once
      # logged in it never checked that the order belonged to the caller —
      # any authenticated user could read anyone else's order by guessing
      # /api/v1/orders/:id (IDOR). Order history is account-scoped, so both
      # actions require a session, and `show` additionally scopes the
      # lookup to current_user.orders instead of Order.find.
      # `create` (checkout) used to be reachable as a guest — the order was
      # simply saved with user: nil. That's why a guest could "place an
      # order" that then vanished the moment they logged in: it was never
      # attached to any account, so `current_user.orders` (index/show)
      # could never find it again. Checkout now requires login too, so the
      # frontend gets a 401 it can turn into "please sign in", and every
      # order that's ever created is guaranteed to belong to a real user
      # and show up in that user's order history afterwards.
      before_action :require_login_json!, only: %i[index show create]

      def index
        orders = current_user.orders.order(created_at: :desc)
        render json: orders.map { |o| serialize_order_summary(o) }
      end

      def show
        order = current_user.orders.find(params[:id])
        render json: serialize_order_detail(order)
      rescue ActiveRecord::RecordNotFound
        # Same response whether the order doesn't exist or just doesn't
        # belong to this user — don't leak which one it is.
        render json: {error: "not_found"}, status: :not_found
      end

      def create
        order = CartService.new(current_cart).checkout!(user: current_user)
        render json: serialize_order_detail(order), status: :created
      rescue ActiveRecord::RecordInvalid
        render json: {error: "cart_empty"}, status: :unprocessable_entity
      end

      private

      # JSON-appropriate alternative to ApplicationController#require_login!
      # (which redirects — fine for HTML pages, wrong for a fetch/axios
      # caller expecting a JSON body and a 401 it can branch on).
      def require_login_json!
        render json: {error: "not_authenticated"}, status: :unauthorized unless current_user
      end

      def serialize_order_summary(order)
        {
          id: order.id,
          number: order.number,
          status: order.status,
          total_price: order.total_price,
          items_count: order.order_items.sum(:quantity),
          created_at: order.created_at
        }
      end

      def serialize_order_detail(order)
        items = order.order_items.includes(:book).map do |item|
          {
            book_id: item.book_id,
            title: item.book.title,
            author: item.book.author,
            cover_image_url: item.book.cover_image_url,
            quantity: item.quantity,
            unit_price: item.unit_price
          }
        end

        {
          id: order.id,
          number: order.number,
          status: order.status,
          total_price: order.total_price,
          created_at: order.created_at,
          items: items
        }
      end
    end
  end
end
