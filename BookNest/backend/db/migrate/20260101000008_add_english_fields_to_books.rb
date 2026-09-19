# == AddEnglishFieldsToBooks =================================================
# Adds nullable English-translation columns for the catalog. Only a subset
# of books (see db/seeds.rb) has these filled in for now — the API falls
# back to the base (Ukrainian) column when the *_en value is blank, so
# books without a translation yet keep working exactly as before.
class AddEnglishFieldsToBooks < ActiveRecord::Migration[7.1]
  def change
    add_column :books, :title_en,       :string
    add_column :books, :author_en,      :string
    add_column :books, :description_en, :text
    add_column :books, :genre_en,       :string
  end
end
