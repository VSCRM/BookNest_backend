module Api
  module V1
    # JSON "saved books" (wishlist) API for the React storefront
    # (frontend-shop's savedBooksService). Keyed by e-mail in the URL, not
    # by id, because — same as Cart — this has to work the instant a
    # JWT-authenticated shopper is first seen by Rails, without a separate
    # signup round-trip.
    #
    # Inherits ApplicationController (not ActionController::API), same
    # reasoning as CartsController: nothing here actually needs cookies,
    # but staying consistent with the rest of the api/v1 namespace is
    # simpler than mixing base classes.
    class UsersController < ApplicationController
      skip_before_action :verify_authenticity_token, raise: false

      # Saved books are per-account, so every action here needs a real,
      # verified identity. `shadow_user` used to build the target row
      # straight from the :username in the URL with no check at all —
      # anyone could read, add to, or clear a *different* shopper's
      # wishlist just by putting their email in the URL (IDOR), and if the
      # JWT cookie hadn't resolved yet on a given request, saves could
      # silently land on the wrong (brand-new, empty) shadow row instead
      # of the signed-in user's — which is exactly what made saved books
      # look like they "disappeared" around login. Requiring login and
      # ignoring the URL param in favor of current_user fixes both.
      before_action :require_login_json!

      # GET /api/v1/users/:username/saved
      def saved_index
        render json: current_user.wishlisted_books.map { |book| serialize_book(book) }
      end

      # POST /api/v1/users/:username/saved
      # Body is the full Book JSON (see savedBooksService.save) — only the
      # id is actually needed to look up the canonical record server-side.
      def saved_create
        book = Book.find(params[:id] || params[:book_id])
        current_user.saved_books.find_or_create_by!(book: book)
        render json: serialize_book(book), status: :created
      rescue ActiveRecord::RecordNotFound
        render json: {error: "book not found"}, status: :not_found
      end

      # DELETE /api/v1/users/:username/saved/:book_id
      def saved_destroy
        current_user.saved_books.where(book_id: params[:book_id]).destroy_all
        head :no_content
      end

      private

      # JSON-appropriate 401 (see OrdersController#require_login_json! —
      # same reasoning, duplicated rather than shared to keep each
      # controller's before_action list self-contained).
      def require_login_json!
        render json: {error: "not_authenticated"}, status: :unauthorized unless current_user
      end

      def serialize_book(book)
        {
          id: book.id,
          title: book.title,
          author: book.author,
          price: book.price,
          genre: book.genre,
          stock: book.stock,
          published_year: book.published_year,
          description: book.description,
          pages: book.pages,
          cover_image_url: book.cover_image_url,
        }
      end
    end
  end
end
