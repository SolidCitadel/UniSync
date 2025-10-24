-- UniSync 마이크로서비스별 데이터베이스 생성
-- 각 서비스는 독립적인 데이터베이스를 사용합니다

-- User Service Database
CREATE DATABASE IF NOT EXISTS user_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- Course Service Database
CREATE DATABASE IF NOT EXISTS course_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- Sync Service Database
CREATE DATABASE IF NOT EXISTS sync_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- Schedule Service Database
CREATE DATABASE IF NOT EXISTS schedule_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- Social Service Database
CREATE DATABASE IF NOT EXISTS social_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- 데이터베이스 목록 확인
SHOW DATABASES;

-- 각 데이터베이스에 대한 전용 사용자 생성 (선택사항)
CREATE USER IF NOT EXISTS 'user_service'@'%' IDENTIFIED BY 'user_service_password';
CREATE USER IF NOT EXISTS 'course_service'@'%' IDENTIFIED BY 'course_service_password';
CREATE USER IF NOT EXISTS 'sync_service'@'%' IDENTIFIED BY 'sync_service_password';
CREATE USER IF NOT EXISTS 'schedule_service'@'%' IDENTIFIED BY 'schedule_service_password';
CREATE USER IF NOT EXISTS 'social_service'@'%' IDENTIFIED BY 'social_service_password';

-- 권한 부여
GRANT ALL PRIVILEGES ON user_db.* TO 'user_service'@'%';
GRANT ALL PRIVILEGES ON course_db.* TO 'course_service'@'%';
GRANT ALL PRIVILEGES ON sync_db.* TO 'sync_service'@'%';
GRANT ALL PRIVILEGES ON schedule_db.* TO 'schedule_service'@'%';
GRANT ALL PRIVILEGES ON social_db.* TO 'social_service'@'%';

-- 공통 사용자에게 모든 데이터베이스 접근 권한 부여 (개발 환경용)
GRANT ALL PRIVILEGES ON *.* TO 'unisync'@'%';

FLUSH PRIVILEGES;
