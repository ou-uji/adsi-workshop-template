-- V1.1: デモ用シードデータ（共通基盤）
-- 目的: 起動直後にログイン→打刻→申請の通しデモができる初期ユーザーを用意する。
-- password_hash は BCryptPasswordEncoder(strength=10) で "password" をハッシュ化した実値。
--   → 全ユーザーの初期パスワードは "password"（デモ用。本番想定では初回変更が必要）。
--   検証済: new BCryptPasswordEncoder().matches("password", <below>) == true

INSERT INTO employee (name, email, password_hash, role) VALUES
    ('管理者 太郎', 'admin@example.com',  '$2a$10$1uFSCQO492vm3amVM.6lW.Q82ytBpM5hBf5IuL.rtQb2VN/sMcnPy', 'ADMIN'),
    ('社員 花子',   'hanako@example.com', '$2a$10$1uFSCQO492vm3amVM.6lW.Q82ytBpM5hBf5IuL.rtQb2VN/sMcnPy', 'MEMBER'),
    ('社員 次郎',   'jiro@example.com',   '$2a$10$1uFSCQO492vm3amVM.6lW.Q82ytBpM5hBf5IuL.rtQb2VN/sMcnPy', 'MEMBER');
