class CreateOrders < ActiveRecord::Migration[7.1]
  def change
    create_table :orders do |t|
      t.references :user, foreign_key: true, null: true
      t.string :number, null: false
      t.string :status, null: false, default: "pending"
      t.timestamps
    end
    add_index :orders, :number, unique: true
  end
end
