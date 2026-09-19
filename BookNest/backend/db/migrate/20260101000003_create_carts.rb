class CreateCarts < ActiveRecord::Migration[7.1]
  def change
    create_table :carts do |t|
      t.references :user, foreign_key: true, null: true
      t.string :session_token
      t.timestamps
    end
    add_index :carts, :session_token, unique: true
  end
end
