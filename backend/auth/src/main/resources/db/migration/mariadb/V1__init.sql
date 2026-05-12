CREATE TABLE auth.`accounts` (
                            `account_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '계정 식별 번호',
                            `email` varchar(255) NOT NULL COMMENT '이메일',
                            `password` varchar(255) NOT NULL COMMENT '비밀번호',
                            `status` enum('ACTIVE','INACTIVE','LOCKED') DEFAULT 'ACTIVE' COMMENT '계정 상태',
                            `failed_login_attempts` int(11) DEFAULT 0 COMMENT '로그인 실패 횟수',
                            `locked_until` datetime DEFAULT NULL COMMENT '계정 잠금 일자',
                            `last_login_at` datetime DEFAULT NULL COMMENT '마지막 로그인 일자',
                            `last_login_ip` varchar(45) DEFAULT NULL COMMENT '마지막 로그인 요청 IP 주소',
                            `password_changed_at` datetime DEFAULT NULL COMMENT '비밀번호 변경 일시',
                            `created_at` datetime NOT NULL COMMENT '계정 생성 일자',
                            `updated_at` datetime NOT NULL COMMENT '계정 변경 일자',
                            PRIMARY KEY (`account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='계정';


CREATE TABLE auth.`email_verifications` (
                                       `verification_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '메일 인증 식별 번호',
                                       `type` varchar(30) NOT NULL COMMENT '메일 인증 타입 (''SIGNUP'', ''PASSWORD_RESET'')',
                                       `email` varchar(255) NOT NULL COMMENT '계정용 이메일',
                                       `verification_code` varchar(255) NOT NULL COMMENT '메일 인증 코드',
                                       `created_at` datetime DEFAULT current_timestamp() COMMENT '메일 인증 코드 생성 일자',
                                       `expires_at` datetime NOT NULL COMMENT '메일 인증 코드 만료 일자',
                                       `verified` tinyint(1) DEFAULT 0 COMMENT '메일 인증 여부',
                                       `verified_at` datetime DEFAULT NULL COMMENT '메일 인증 완료 일자',
                                       `attempts` int(11) DEFAULT 0 COMMENT '메일 인증 시도 횟수',
                                       PRIMARY KEY (`verification_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;


CREATE TABLE auth.`login_logs` (
                              `log_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '로그인 이력 식별 번호',
                              `account_id` bigint(20) DEFAULT NULL COMMENT '계정 식별 번호',
                              `login_status` enum('SUCCESS','FAILED_WRONG_PASSWORD','FAILED_ACCOUNT_LOCKED','FAILED_ACCOUNT_NOT_FOUND') NOT NULL COMMENT '로그인 상태',
                              `attempted_email` varchar(255) DEFAULT NULL COMMENT '로그인을 시도한 메일',
                              `login_at` datetime DEFAULT current_timestamp() COMMENT '로그인 일자',
                              `ip_address` varchar(45) DEFAULT NULL COMMENT '로그인 요청 IP 주소',
                              `user_agent` text DEFAULT NULL COMMENT '클라이언트 정보',
                              PRIMARY KEY (`log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='로그인 이력';


CREATE TABLE auth.`password_histories` (
                                      `history_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '비밀번호 변경 이력 식별 번호',
                                      `account_id` bigint(20) NOT NULL COMMENT '계정 식별 번호',
                                      `password_hash` varchar(255) NOT NULL COMMENT '해시 비밀번호',
                                      `changed_at` datetime DEFAULT current_timestamp() COMMENT '변경 일자',
                                      `password_reset_method` enum('USER_CHANGE','EMAIL_RESET','ADMIN_RESET','FORCE_RESET') NOT NULL COMMENT '변경 방법',
                                      `ip_address` varchar(45) DEFAULT NULL COMMENT '요청 IP',
                                      `user_agent` text DEFAULT NULL COMMENT '클라이언트 정보',
                                      PRIMARY KEY (`history_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='비밀번호 변경 이력';


CREATE TABLE auth.`refresh_tokens` (
                                  `token_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '리프래시 토큰 식별 번호',
                                  `account_id` bigint(20) NOT NULL COMMENT '계정 식별 번호',
                                  `jti` varchar(255) NOT NULL COMMENT 'JWT jti 검증용',
                                  `token_hash` varchar(255) NOT NULL COMMENT '리프래시 토큰',
                                  `issued_at` datetime DEFAULT current_timestamp() COMMENT '리프래시 토큰 발행 일자',
                                  `expires_at` datetime NOT NULL COMMENT '리프래시 토큰 만료 일자',
                                  `revoked_at` datetime DEFAULT NULL COMMENT '리프래시 토큰 무효화 일자',
                                  `is_revoked` tinyint(1) DEFAULT 0 COMMENT '리프래시 토큰 무효화 여부',
                                  `ip_address` varchar(45) DEFAULT NULL COMMENT '리프래시 토큰 요청 IP 주소',
                                  `user_agent` text DEFAULT NULL COMMENT '클라이언트 정보',
                                  PRIMARY KEY (`token_id`),
                                  UNIQUE KEY `refresh_tokens_unique` (`jti`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='리프래시 토큰';