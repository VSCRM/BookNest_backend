module Api
  module V1
    # JSON API for the storefront — used by the Swagger docs and by the
    # React/Vite app (frontend-shop).
    #
    # Accepts an optional `?locale=en` param (sent by frontend-shop's
    # bookService whenever the person switches the UI language). Only
    # title/author/description/genre are localized — everything else
    # (price, stock, images, etc.) is locale-independent.
    class BooksController < ActionController::API
      def index
        books = Book.search_by_term(params[:q]).by_genre(params[:genre]).order_price(params[:sort])
        render json: books.map { |book| localized_json(book) }
      end

      def show
        render json: localized_json(Book.find(params[:id]))
      end

      private

      def localized_json(book)
        {
          id: book.id,
          title: book.localized(:title, params[:locale]),
          author: book.localized(:author, params[:locale]),
          price: book.price,
          genre: book.localized(:genre, params[:locale]),
          stock: book.stock,
          published_year: book.published_year,
          description: book.localized(:description, params[:locale]),
          pages: book.pages,
          cover_image_url: book.cover_image_url,
        }
      end
    end
  end
end
