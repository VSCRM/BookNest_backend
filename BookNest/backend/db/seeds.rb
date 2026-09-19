# Seed data for the BookNest catalog.

# Simple placeholder covers (placehold.co) in a warm paper palette so the
# catalog looks complete without storing real image files in the repo.
# Swap for real URLs / Active Storage later — trivial, since both Rails
# and the frontend only ever read the cover_image_url field.
def cover_url(title)
  "https://placehold.co/400x600/EBD9B4/3A2F28?font=playfair-display&text=#{ERB::Util.url_encode(title)}"
end

books = [
  # ── Harry Potter, all 7 parts, in order (J.K. Rowling) ──────────
  { title: "Гаррі Поттер і філософський камінь", author: "Джоан Роулінг", price: 320, genre: "Фентезі",
    pages: 320, published_year: 1997, stock: 20,
    description: "Перша книга саги: хлопець дізнається, що він чарівник, і вирушає до Гоґвортсу.",
    title_en: "Harry Potter and the Philosopher's Stone", author_en: "J.K. Rowling", genre_en: "Fantasy",
    description_en: "The first book of the saga: a boy learns he's a wizard and heads off to Hogwarts." },
  { title: "Гаррі Поттер і таємна кімната", author: "Джоан Роулінг", price: 320, genre: "Фентезі",
    pages: 336, published_year: 1998, stock: 18,
    description: "Другий рік у Гоґвортсі: хтось відкрив Таємну кімнату.",
    title_en: "Harry Potter and the Chamber of Secrets", author_en: "J.K. Rowling", genre_en: "Fantasy",
    description_en: "Second year at Hogwarts: someone has opened the Chamber of Secrets." },
  { title: "Гаррі Поттер і в'язень Азкабану", author: "Джоан Роулінг", price: 330, genre: "Фентезі",
    pages: 448, published_year: 1999, stock: 16,
    description: "Третій рік: втеча небезпечного в'язня з Азкабану.",
    title_en: "Harry Potter and the Prisoner of Azkaban", author_en: "J.K. Rowling", genre_en: "Fantasy",
    description_en: "Third year: a dangerous prisoner has escaped from Azkaban." },
  { title: "Гаррі Поттер і келих вогню", author: "Джоан Роулінг", price: 360, genre: "Фентезі",
    pages: 640, published_year: 2000, stock: 14,
    description: "Турнір трьох чарівників і повернення темного лорда.",
    title_en: "Harry Potter and the Goblet of Fire", author_en: "J.K. Rowling", genre_en: "Fantasy",
    description_en: "The Triwizard Tournament, and the dark lord's return." },
  { title: "Гаррі Поттер і орден Фенікса", author: "Джоан Роулінг", price: 390, genre: "Фентезі",
    pages: 768, published_year: 2003, stock: 12,
    description: "П'ятий рік: таємне товариство протистоїть Міністерству магії.",
    title_en: "Harry Potter and the Order of the Phoenix", author_en: "J.K. Rowling", genre_en: "Fantasy",
    description_en: "Fifth year: a secret society stands against the Ministry of Magic." },
  { title: "Гаррі Поттер і напівкровний принц", author: "Джоан Роулінг", price: 370, genre: "Фентезі",
    pages: 608, published_year: 2005, stock: 12,
    description: "Шостий рік: минуле Волдеморта і таємничий підручник.",
    title_en: "Harry Potter and the Half-Blood Prince", author_en: "J.K. Rowling", genre_en: "Fantasy",
    description_en: "Sixth year: Voldemort's past, and a mysterious textbook." },
  { title: "Гаррі Поттер і смертельні реліквії", author: "Джоан Роулінг", price: 400, genre: "Фентезі",
    pages: 704, published_year: 2007, stock: 10,
    description: "Фінал саги: полювання на горокракси і остання битва.",
    title_en: "Harry Potter and the Deathly Hallows", author_en: "J.K. Rowling", genre_en: "Fantasy",
    description_en: "The saga's finale: the hunt for Horcruxes and the final battle." },

  # ── Percy Jackson, first 5 books (Rick Riordan) ──────────────────
  { title: "Персі Джексон і Викрадач блискавок", author: "Рік Ріордан", price: 280, genre: "Фентезі",
    pages: 375, published_year: 2005, stock: 15,
    description: "Підліток дізнається, що він син Посейдона, і вирушає рятувати світ богів.",
    title_en: "The Lightning Thief", author_en: "Rick Riordan", genre_en: "Fantasy",
    description_en: "A teenager discovers he's the son of Poseidon and must save the world of gods." },
  { title: "Персі Джексон і Море чудовиськ", author: "Рік Ріордан", price: 280, genre: "Фентезі",
    pages: 279, published_year: 2006, stock: 14,
    description: "Пошуки Золотого руна крізь Море чудовиськ (Бермудський трикутник).",
    title_en: "The Sea of Monsters", author_en: "Rick Riordan", genre_en: "Fantasy",
    description_en: "A quest for the Golden Fleece across the Sea of Monsters (the Bermuda Triangle)." },
  { title: "Персі Джексон і Прокляття титана", author: "Рік Ріордан", price: 290, genre: "Фентезі",
    pages: 312, published_year: 2007, stock: 13,
    description: "Артеміда зникає, а Персі вирушає у пошуки разом із новими друзями.",
    title_en: "The Titan's Curse", author_en: "Rick Riordan", genre_en: "Fantasy",
    description_en: "Artemis vanishes, and Percy embarks on a quest with new companions." },
  { title: "Персі Джексон і Битва в лабіринті", author: "Рік Ріордан", price: 290, genre: "Фентезі",
    pages: 361, published_year: 2008, stock: 12,
    description: "Лабіринт Дедала веде просто під табір напівкровних.",
    title_en: "The Battle of the Labyrinth", author_en: "Rick Riordan", genre_en: "Fantasy",
    description_en: "Daedalus's Labyrinth leads straight beneath the demigod camp." },
  { title: "Персі Джексон і Останній олімпієць", author: "Рік Ріордан", price: 300, genre: "Фентезі",
    pages: 381, published_year: 2009, stock: 11,
    description: "Фінальна битва за Олімп у серці Мангеттена.",
    title_en: "The Last Olympian", author_en: "Rick Riordan", genre_en: "Fantasy",
    description_en: "The final battle for Olympus at the heart of Manhattan." },

  # ── The Witcher saga, in order (Andrzej Sapkowski) ───────────────
  { title: "Відьмак. Останнє бажання", author: "Анджей Сапковський", price: 300, genre: "Фентезі",
    pages: 288, published_year: 1993, stock: 12,
    description: "Збірка оповідань, що знайомить із відьмаком Ґеральтом із Рівії.",
    title_en: "The Last Wish", author_en: "Andrzej Sapkowski", genre_en: "Fantasy",
    description_en: "A collection of stories introducing the witcher Geralt of Rivia." },
  { title: "Відьмак. Меч призначення", author: "Анджей Сапковський", price: 300, genre: "Фентезі",
    pages: 384, published_year: 1992, stock: 11,
    description: "Другий збірник оповідань, що передує головному цикл.",
    title_en: "Sword of Destiny", author_en: "Andrzej Sapkowski", genre_en: "Fantasy",
    description_en: "The second collection of stories preceding the main saga cycle." },
  { title: "Відьмак. Кров ельфів", author: "Анджей Сапковський", price: 320, genre: "Фентезі",
    pages: 384, published_year: 1994, stock: 10,
    description: "Перший том основної саги: доля Цірі стає ключовою.",
    title_en: "Blood of Elves", author_en: "Andrzej Sapkowski", genre_en: "Fantasy",
    description_en: "The first volume of the main saga: Ciri's fate becomes crucial." },
  { title: "Відьмак. Час погорди", author: "Анджей Сапковський", price: 320, genre: "Фентезі",
    pages: 320, published_year: 1995, stock: 9,
    description: "Війна насувається на Північні королівства.",
    title_en: "Time of Contempt", author_en: "Andrzej Sapkowski", genre_en: "Fantasy",
    description_en: "War advances on the Northern Kingdoms." },
  { title: "Відьмак. Хрещення вогнем", author: "Анджей Сапковський", price: 320, genre: "Фентезі",
    pages: 352, published_year: 1996, stock: 9,
    description: "Ґеральт збирає загін, щоб знайти зниклу Цірі.",
    title_en: "Baptism of Fire", author_en: "Andrzej Sapkowski", genre_en: "Fantasy",
    description_en: "Geralt assembles a company to find the missing Ciri." },
  { title: "Відьмак. Вежа Ластівки", author: "Анджей Сапковський", price: 330, genre: "Фентезі",
    pages: 448, published_year: 1997, stock: 8,
    description: "Цірі тікає крізь час і простір, рятуючись від переслідувачів.",
    title_en: "The Tower of Swallows", author_en: "Andrzej Sapkowski", genre_en: "Fantasy",
    description_en: "Ciri flees through time and space, escaping her pursuers." },
  { title: "Відьмак. Володарка Озера", author: "Анджей Сапковський", price: 340, genre: "Фентезі",
    pages: 448, published_year: 1999, stock: 8,
    description: "Завершення саги про відьмака Ґеральта і його прийомну доньку.",
    title_en: "The Lady of the Lake", author_en: "Andrzej Sapkowski", genre_en: "Fantasy",
    description_en: "The conclusion of the saga of the witcher Geralt and his adoptive daughter." },

  # ── Howl's Moving Castle, all 3 parts (Diana Wynne Jones) ────────
  { title: "Мандрівний замок Хаула", author: "Діана Вінн Джонс", price: 300, genre: "Фентезі",
    pages: 320, published_year: 1986, stock: 11,
    description: "Софі перетворена на стареньку і оселяється в мандрівному замку чарівника Хаула.",
    title_en: "Howl's Moving Castle", author_en: "Diana Wynne Jones", genre_en: "Fantasy",
    description_en: "Sophie is transformed into an old woman and settles in the sorcerer Howl's moving castle." },
  { title: "Повітряний замок", author: "Діана Вінн Джонс", price: 300, genre: "Фентезі",
    pages: 336, published_year: 1990, stock: 9,
    description: "Друга книга циклу: нові пригоди у світі летючих килимів і духів.",
    title_en: "Castle in the Air", author_en: "Diana Wynne Jones", genre_en: "Fantasy",
    description_en: "The second book of the cycle: new adventures in a world of flying carpets and spirits." },
  { title: "Дім із багатьма шляхами", author: "Діана Вінн Джонс", price: 300, genre: "Фентезі",
    pages: 344, published_year: 2008, stock: 8,
    description: "Третя книга циклу: чарівний будинок із дверима в різні місця.",
    title_en: "House of Many Ways", author_en: "Diana Wynne Jones", genre_en: "Fantasy",
    description_en: "The third book of the cycle: a magical house with doors to different places." },

  # ── Software engineering (chosen instead of a plain C++ book) ────
  { title: "Чистий код", author: "Роберт Мартін", price: 450, genre: "IT",
    pages: 464, published_year: 2008, stock: 10,
    description: "Практичний посібник з написання зрозумілого, підтримуваного коду.",
    title_en: "Clean Code", author_en: "Robert C. Martin", genre_en: "IT",
    description_en: "A practical guide to writing clean, maintainable code." },
  { title: "Прийоми об'єктно-орієнтованого проєктування. Патерни проєктування",
    author: "Еріх Гамма, Річард Хелм, Ральф Джонсон, Джон Вліссідес", price: 470, genre: "IT",
    pages: 416, published_year: 1994, stock: 8,
    description: "Класична книга «банди чотирьох» про патерни проєктування ПЗ.",
    title_en: "Design Patterns: Elements of Reusable Object-Oriented Software",
    author_en: "Erich Gamma, Richard Helm, Ralph Johnson, John Vlissides", genre_en: "IT",
    description_en: "The classic \"Gang of Four\" book on software design patterns." },
]

books.each do |attrs|
  attrs[:cover_image_url] = cover_url(attrs[:title])
  # Always sync the full attrs on every run (not just cover_image_url) so
  # re-running `db:seed` after adding the *_en columns backfills the
  # translation on books that already existed in the database.
  Book.find_or_create_by!(title: attrs[:title]) { |b| b.assign_attributes(attrs) }
    .update!(attrs)
end

# Removed titles from a previous seed run stay out of the way of the demo
# catalog above (keeps `rails db:seed` idempotent when re-run after this
# book list changed).
current_titles = books.map { |b| b[:title] }
Book.where.not(title: current_titles).destroy_all

# Admin/guest credentials come from the environment so anyone running this
# project can set their own instead of relying on a hardcoded pair. See
# .env.example for ADMIN_EMAIL / ADMIN_PASSWORD / GUEST_EMAIL / GUEST_PASSWORD.
# Admin/guest credentials come from the environment so anyone running this
# project can set their own instead of relying on a hardcoded pair. See
# .env.example for ADMIN_EMAIL / ADMIN_PASSWORD / GUEST_EMAIL / GUEST_PASSWORD.
#
# To seed MULTIPLE admins, set ADMIN_ACCOUNTS="email1:password1,email2:password2"
# instead — it takes priority over the single ADMIN_EMAIL/ADMIN_PASSWORD pair.
# Mirrors auth-service/AdminSeeder.java so both services agree on who's admin.
admin_accounts =
  if ENV["ADMIN_ACCOUNTS"].present?
    ENV["ADMIN_ACCOUNTS"].split(",").filter_map do |entry|
      email, password = entry.split(":", 2)
      if email.blank? || password.blank?
        warn "Skipping malformed ADMIN_ACCOUNTS entry (expected email:password): #{entry}"
        next
      end
      [email.strip, password]
    end
  else
    [[ENV.fetch("ADMIN_EMAIL", "admin@booknest.local"), ENV.fetch("ADMIN_PASSWORD", "admin12345")]]
  end

admin_accounts.each do |email, password|
  admin = User.find_or_initialize_by(email: email)
  admin.name = "Адміністратор BookNest"
  admin.password = password
  admin.role = "admin"
  admin.save!
end

guest_email    = ENV.fetch("GUEST_EMAIL", "guest@booknest.local")
guest_password = ENV.fetch("GUEST_PASSWORD", "guest12345")

guest = User.find_or_initialize_by(email: guest_email)
guest.name = "Тестовий покупець"
guest.password = guest_password
guest.role = "customer"
guest.save!

puts "Seed done: #{Book.count} books, #{User.count} users (admins: #{admin_accounts.map(&:first).join(', ')})"
