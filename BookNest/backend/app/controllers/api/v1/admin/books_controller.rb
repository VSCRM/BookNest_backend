module Api
  module V1
    module Admin
      # == Api::V1::Admin::BooksController =====================================
      # JSON catalog-management API for the React admin panel (frontend-shop's
      # "Add book" tab, shown only to `user.role === 'admin'`).
      #
      # Inherits ApplicationController (not ActionController::API) for the same
      # reason as CartsController/OrdersController: current_user relies on the
      # `booknest_jwt` cookie, which needs cookies/session support that
      # ActionController::API does not provide.
      #
      # This is intentionally a separate controller from the legacy
      # /rubyback/admin/books (::Admin::BooksController, server-rendered ERB) —
      # that one keeps working unchanged for the hidden legacy site; this one
      # exists purely so the React SPA has something to POST/PATCH/DELETE to.
      class BooksController < ApplicationController
        skip_before_action :verify_authenticity_token, raise: false
        before_action :require_login_json!
        before_action :require_admin_json!
        before_action :set_book, only: %i[update destroy]

        # GET /api/v1/admin/books
        # Full (non-localized) catalog, including English fields — the admin
        # table needs both languages visible at once to edit them.
        def index
          render json: Book.order(:title).map { |book| full_json(book) }
        end

        # POST /api/v1/admin/books
        # Accepts multipart/form-data (for the optional cover_image file) or
        # plain JSON (when cover_image_url is a pasted URL instead).
        def create
          book = Book.new(book_params)
          assign_cover_image!(book)

          if book.valid?(:admin_write) && book.save
            render json: full_json(book), status: :created
          else
            render json: { errors: book.errors.messages }, status: :unprocessable_entity
          end
        end

        # PATCH/PUT /api/v1/admin/books/:id
        def update
          @book.assign_attributes(book_params)
          assign_cover_image!(@book)

          if @book.valid?(:admin_write) && @book.save
            render json: full_json(@book)
          else
            render json: { errors: @book.errors.messages }, status: :unprocessable_entity
          end
        end

        # DELETE /api/v1/admin/books/:id
        def destroy
          @book.destroy
          head :no_content
        rescue ActiveRecord::InvalidForeignKey, ActiveRecord::DeleteRestrictionError
          # Books with existing order_items can't be hard-deleted (see
          # has_many :order_items, dependent: :restrict_with_error) — keep
          # order history intact instead of silently corrupting past orders.
          render json: { error: "book_has_orders" }, status: :unprocessable_entity
        end

        private

        def set_book
          @book = Book.find(params[:id])
        end

        # JSON-appropriate guards (see Api::V1::OrdersController for the same
        # pattern) — ApplicationController#require_login!/#require_admin!
        # redirect_to an HTML path, which is wrong for a fetch/axios caller.
        def require_login_json!
          render json: { error: "not_authenticated" }, status: :unauthorized unless current_user
        end

        def require_admin_json!
          render json: { error: "not_authorized" }, status: :forbidden unless current_user&.admin?
        end

        def book_params
          params.require(:book).permit(
            :title, :author, :price, :description, :genre, :pages,
            :published_year, :stock, :cover_image_url,
            :title_en, :author_en, :description_en, :genre_en
          )
        end

        # Handles the optional uploaded cover image (multipart `book[cover_image]`).
        # Stores it under public/uploads/covers and points cover_image_url at an
        # absolute URL (relative paths would fail the frontend's Zod `.url()`
        # check and wouldn't work from a different origin either).
        # `book[remove_cover_image]=true` clears an existing image back to the
        # frontend's default placeholder without uploading a new one.
        def assign_cover_image!(book)
          upload = params.dig(:book, :cover_image)
          if upload.present?
            filename = "#{SecureRandom.uuid}#{File.extname(upload.original_filename.to_s).downcase}"
            dest_dir = Rails.root.join("public", "uploads", "covers")
            FileUtils.mkdir_p(dest_dir)
            File.binwrite(dest_dir.join(filename), upload.read)
            book.cover_image_url = "#{request.base_url}/uploads/covers/#{filename}"
          elsif ActiveModel::Type::Boolean.new.cast(params.dig(:book, :remove_cover_image))
            book.cover_image_url = nil
          end
        end

        def full_json(book)
          {
            id: book.id,
            title: book.title,
            title_en: book.title_en,
            author: book.author,
            author_en: book.author_en,
            price: book.price,
            genre: book.genre,
            genre_en: book.genre_en,
            stock: book.stock,
            published_year: book.published_year,
            description: book.description,
            description_en: book.description_en,
            pages: book.pages,
            cover_image_url: book.cover_image_url,
          }
        end
      end
    end
  end
end
