# == Book ==============================================================
# Catalog domain model (SRS 5.1: books table).
# Responsibility (SRP): storing and basic-validating book data, plus a
# few simple query scopes for the storefront.
class Book < ApplicationRecord
  has_many :cart_items,  dependent: :destroy
  has_many :order_items, dependent: :restrict_with_error
  has_many :saved_books, dependent: :destroy

  validates :title,  presence: true
  validates :author, presence: true
  validates :price,  presence: true, numericality: { greater_than_or_equal_to: 0 }
  validates :stock,  numericality: { greater_than_or_equal_to: 0 }

  # English translations are required whenever a book is being managed
  # through the admin panel (Api::V1::Admin::BooksController), so the
  # catalog never regresses to Ukrainian-only again once an admin touches
  # a record. `on: :admin_write` keeps this scoped to that one context —
  # legacy/seeded rows that never got an English translation can still be
  # read, and the old /rubyback ERB admin (Admin::BooksController) is
  # unaffected — so nothing that already worked breaks.
  validates :title_en,       presence: true, on: :admin_write
  validates :author_en,      presence: true, on: :admin_write
  validates :description_en, presence: true, on: :admin_write
  validates :genre_en,       presence: true, on: :admin_write

  scope :search_by_term, ->(term) {
    return all if term.blank?

    like = "%#{sanitize_sql_like(term)}%"
    where("title LIKE :q OR author LIKE :q", q: like)
  }

  scope :by_genre,   ->(genre)  { genre.present? ? where(genre: genre) : all }
  scope :order_price, ->(dir)   { dir == "desc" ? order(price: :desc) : order(price: :asc) }

  def in_stock?
    stock.to_i.positive?
  end

  def formatted_price
    "#{'%.2f' % price} грн"
  end

  # Locale-aware field lookup for the JSON API. Falls back to the base
  # (Ukrainian) column when no translation is filled in yet, so books
  # without an *_en value keep rendering exactly as before instead of
  # showing blank fields.
  def localized(field, locale)
    return self[field] unless locale.to_s == "en"

    self["#{field}_en"].presence || self[field]
  end
end
