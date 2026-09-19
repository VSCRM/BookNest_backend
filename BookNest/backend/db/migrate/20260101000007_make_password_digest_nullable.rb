# Identity now lives in the Spring Boot auth-service; JWT-authenticated
# shadow users created by Rails have no local password at all.
class MakePasswordDigestNullable < ActiveRecord::Migration[7.1]
  def change
    change_column_null :users, :password_digest, true
  end
end
