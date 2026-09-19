# == WelcomeController =======================================================
# A small interactive easter-egg page. Not linked from anywhere in the app —
# reachable only by typing /welcome directly in the address bar. Replaces the
# old static "Hello, Rails!" placeholder with a self-contained animated page
# (see app/views/layouts/welcome.html.erb for the full-screen bare layout).
class WelcomeController < ApplicationController
  layout "welcome"

  def index; end
end
