class CreateCartItems < ActiveRecord::Migration[7.1]
  def change
    create_table :cart_items do |t|
      t.references :cart, null: false, foreign_key: true
      t.references :book, null: false, foreign_key: true
      t.integer :quantity, null: false, default: 1
      t.timestamps
    end
    add_index :cart_items, %i[cart_id book_id], unique: true
  end
end
