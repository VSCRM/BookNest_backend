# == Order ================================================================
# SRS 3.3.2 (FR-6): once placed, a unique order number is generated and
# shown to the customer as confirmation.
class Order < ApplicationRecord
  belongs_to :user, optional: true
  has_many :order_items, dependent: :destroy

  before_create :generate_order_number

  enum :status, { pending: "pending", confirmed: "confirmed" }, default: :pending

  def total_price
    order_items.sum { |i| i.unit_price * i.quantity }
  end

  private

  def generate_order_number
    self.number ||= "BN-#{Time.current.strftime('%Y%m%d')}-#{SecureRandom.hex(3).upcase}"
  end
end
