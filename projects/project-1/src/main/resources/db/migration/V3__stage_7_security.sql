-- Dodaje hash hasła użytkownika aplikacji.
--
-- Kolumna jest potrzebna dla AuthService.login(...), który pobiera użytkownika
-- po emailu i sprawdza:
--
-- user.getPasswordHash()
--
-- Przechowujemy hash hasła, nigdy hasło jawne.
--
-- IF NOT EXISTS pozwala odpalić migrację ponownie bez błędu,
-- jeśli kolumna została już dodana wcześniej.
ALTER TABLE app_users
    ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);

-- Tworzy tabelę refresh tokenów.
--
-- Refresh token służy do wydawania nowych access tokenów bez ponownego logowania.
--
-- W bazie zapisujemy token_hash, czyli hash refresh tokenu,
-- a nie surową wartość tokenu.
--
-- Dzięki temu wyciek bazy nie ujawnia bezpośrednio aktywnych refresh tokenów.
CREATE TABLE IF NOT EXISTS refresh_tokens (
    -- Główny identyfikator rekordu refresh tokenu.
                                              id UUID PRIMARY KEY,

    -- Użytkownik, do którego należy refresh token.
    --
    -- Jeden użytkownik może mieć wiele refresh tokenów,
    -- np. dla różnych urządzeń albo sesji.
                                              user_id UUID NOT NULL REFERENCES app_users(id),

    -- Hash refresh tokenu.
    --
    -- UNIQUE wymusza, że ten sam hash tokenu nie pojawi się dwa razy.
    -- AuthService.refresh(...) wyszukuje token właśnie po tym polu.
    token_hash VARCHAR(128) NOT NULL UNIQUE,

    -- Moment utworzenia refresh tokenu.
    created_at TIMESTAMPTZ NOT NULL,

    -- Moment wygaśnięcia refresh tokenu.
    --
    -- Po tym czasie token nie powinien pozwalać na odświeżenie access tokenu.
    expires_at TIMESTAMPTZ NOT NULL,

    -- Moment unieważnienia tokenu.
    --
    -- NULL oznacza, że token nie został jeszcze unieważniony.
    -- W rotacji refresh tokenów stary token dostaje revoked_at po użyciu.
    revoked_at TIMESTAMPTZ
    );

-- Indeks po token_hash.
--
-- Technicznie UNIQUE na token_hash zwykle i tak tworzy indeks.
-- Ten jawny indeks może być więc redundantny zależnie od bazy/migracji.
--
-- W projekcie edukacyjnym jest czytelny, bo pokazuje access pattern:
-- refresh token lookup po token_hash.
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token_hash
    ON refresh_tokens(token_hash);

-- Indeks po user_id.
--
-- Przydatny, jeśli będziesz chciał:
-- - znaleźć wszystkie refresh tokeny użytkownika,
-- - unieważnić wszystkie sesje użytkownika,
-- - sprzątać stare tokeny,
-- - analizować aktywne sesje.
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id
    ON refresh_tokens(user_id);