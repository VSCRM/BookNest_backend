class CreateBooks < ActiveRecord::Migration[7.1]
  def change
    create_table :books do |t|
      t.string  :title, null: false
      t.string  :author, null: false
      t.decimal :price, precision: 8, scale: 2, null: false, default: 0
      t.text    :description
      t.string  :genre
      t.integer :pages
      t.integer :published_year
      t.integer :stock, default: 0, null: false
      t.string  :cover_image_url
      t.timestamps
    end
    add_index :books, :genre
  end
end
