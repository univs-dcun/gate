-- gate.api_keys definition

CREATE TABLE `api_keys` (
                            `api_key_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'API KEY 식별 번호',
                            `project_id` bigint(20) NOT NULL COMMENT '프로젝트 식별 번호',
                            `api_key` varchar(255) NOT NULL COMMENT 'API KEY',
                            `secret_key` varchar(255) NOT NULL COMMENT 'API Secret Key',
                            `issued_at` datetime NOT NULL DEFAULT current_timestamp() COMMENT 'API KEY 발행 일자',
                            `expires_at` datetime NOT NULL COMMENT 'API KEY 만료 일자',
                            `is_active` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'API KEY 활성화 여부',
                            `created_at` datetime NOT NULL DEFAULT current_timestamp() COMMENT 'API KEY 생성 일자',
                            `updated_at` datetime NOT NULL DEFAULT current_timestamp() COMMENT 'API KEY 변경 일자',
                            PRIMARY KEY (`api_key_id`),
                            UNIQUE KEY `api_keys_unique` (`api_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='API 키';


-- gate.companies definition

CREATE TABLE `companies` (
                             `company_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '기업 식별 번호',
                             `account_id` bigint(20) NOT NULL COMMENT '계정 식별 변호',
                             `company_name` varchar(100) DEFAULT '' COMMENT '회사 이름',
                             `business_number` varchar(20) DEFAULT '' COMMENT '기업 고유 식별자',
                             `manager_mail` varchar(255) DEFAULT '' COMMENT '담당자 메일',
                             `manager_name` varchar(100) DEFAULT '' COMMENT '담당자 이름',
                             `manager_number` varchar(100) DEFAULT '' COMMENT '담당자 전화번호',
                             `main_service` varchar(100) DEFAULT '' COMMENT '메인 서비스',
                             `business_type` varchar(100) DEFAULT '' COMMENT '업태',
                             `employee_count` varchar(100) DEFAULT '' COMMENT '직원수',
                             `created_by` bigint(20) NOT NULL COMMENT '기업 생성자',
                             `created_at` datetime NOT NULL COMMENT '기업 생성 일자',
                             `updated_by` bigint(20) NOT NULL COMMENT '기업 정보 변경자',
                             `updated_at` datetime NOT NULL COMMENT '기업 정보 변경 일자',
                             PRIMARY KEY (`company_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='기업';


-- gate.consent_logs definition

CREATE TABLE `consent_logs` (
                                `consent_log_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '이용 약관 이력 식별 번호',
                                `project_id` bigint(20) NOT NULL COMMENT '프로젝트 식별 번호',
                                `end_user_identifier` bigint(20) NOT NULL COMMENT '이용 약관 변경자',
                                `consent_type` enum('PRIVACY','TERMS') NOT NULL DEFAULT 'PRIVACY' COMMENT '이용 약관 여부 타입',
                                `agreed` tinyint(1) NOT NULL DEFAULT 0 COMMENT '이용 약관 동의 여부',
                                `ip_address` varchar(255) DEFAULT NULL COMMENT '요청 IP',
                                `agreed_at` datetime DEFAULT NULL COMMENT '이용 약관 동의 일자',
                                `created_at` datetime NOT NULL DEFAULT current_timestamp() COMMENT '이용 약관 생성 일자',
                                PRIMARY KEY (`consent_log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='이용 약관 이력';


-- gate.match_history definition

CREATE TABLE `match_history` (
                                 `match_history_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '매치 식별 번호',
                                 `project_id` bigint(20) NOT NULL COMMENT '프로젝트 식별 번호',
                                 `match_type` enum('REGISTER','IDENTIFY','VERIFY','LIVENESS') NOT NULL COMMENT '매치 타입',
                                 `match_time` datetime NOT NULL COMMENT '매치 시간',
                                 `check_liveness` tinyint(1) NOT NULL COMMENT '라이브니스 적용 여부',
                                 `success` tinyint(1) NOT NULL COMMENT '매치 성공 여부',
                                 `user_id` bigint(20) DEFAULT NULL COMMENT '매치 성공시 사용자 식별 번호',
                                 `face_id` varchar(100) DEFAULT '' COMMENT '얼굴 식별자',
                                 `user_description` varchar(1000) DEFAULT '' COMMENT '사용자 정보',
                                 `similarity` decimal(5,2) DEFAULT 0.00 COMMENT '매치 유사도 점수',
                                 `face_image_path` varchar(100) DEFAULT '' COMMENT '원본 이미지 경로',
                                 `match_face_image_path` varchar(100) DEFAULT '' COMMENT '매치 이미지 경로',
                                 `match_face_id` varchar(100) DEFAULT NULL COMMENT '1:1 매치에서 매치 대상 얼굴 아이디',
                                 `failure_type` varchar(100) DEFAULT '' COMMENT '실패 타입',
                                 `failure_reason` varchar(1000) DEFAULT '' COMMENT '실패 사유',
                                 `transaction_uuid` varchar(36) NOT NULL COMMENT '트랜잭션 키',
                                 `created_by` bigint(20) NOT NULL COMMENT '매치 이력 생성자',
                                 `created_at` datetime NOT NULL COMMENT '매치 이력 생성 일자',
                                 `updated_by` bigint(20) NOT NULL COMMENT '매치 이력 변경자',
                                 `updated_at` datetime NOT NULL COMMENT '매치 이력 변경 일자',
                                 PRIMARY KEY (`match_history_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='매칭 이력';


-- gate.project_settings definition

CREATE TABLE `project_settings` (
                                    `setting_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '프로젝트 설정 식별 번호',
                                    `project_id` bigint(20) NOT NULL COMMENT '프로젝트 식별 번호',
                                    `demo_enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '데모 활성화 여부',
                                    `consent_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '이용 약관 여부',
                                    `consent_agreed_at` datetime DEFAULT NULL COMMENT '이용 약관 동의 일자',
                                    `liveness_recording_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '얼굴 등록 라이브니스 적용 여부',
                                    `liveness_identifying_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '얼굴 1:N 매칭 라이브니스 적용 여부',
                                    `liveness_verifying_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '얼굴 1:1 매칭 라이브니스 적용 여부',
                                    `created_at` datetime NOT NULL DEFAULT current_timestamp() COMMENT '프로젝트 설정 생성 일자',
                                    `updated_at` datetime NOT NULL DEFAULT current_timestamp() COMMENT '프로젝트 설정 변경 일자',
                                    PRIMARY KEY (`setting_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='프로젝트 설정';


-- gate.projects definition

CREATE TABLE `projects` (
                            `project_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '프로젝트 식별 번호',
                            `account_id` bigint(20) NOT NULL COMMENT '계정 식별 번호',
                            `project_name` varchar(255) NOT NULL COMMENT '프로젝트 이름',
                            `project_description` varchar(1000) DEFAULT '' COMMENT '프로젝트 설명',
                            `status` enum('ACTIVE','INACTIVE','DELETED') DEFAULT 'ACTIVE' COMMENT '프로젝트 상태',
                            `project_type` enum('STANDARD','EXTERNAL') NOT NULL DEFAULT 'STANDARD' COMMENT '프로젝트 타입',
                            `project_module_type` enum('FACE','PALM') NOT NULL DEFAULT 'FACE' COMMENT '프로젝트 모듈 타입',
                            `package_key` varchar(99) DEFAULT NULL COMMENT '외부 연동 모듈 키',
                            `is_deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '프로젝트 삭제 여부',
                            `branch_name` varchar(36) NOT NULL COMMENT '특징점 바운더리 키',
                            `created_by` bigint(20) NOT NULL COMMENT '프로젝트 생성자',
                            `created_at` datetime NOT NULL DEFAULT current_timestamp() COMMENT '프로젝트 생성 일자',
                            `updated_by` bigint(20) NOT NULL COMMENT '프로젝트 변경자',
                            `updated_at` datetime NOT NULL DEFAULT current_timestamp() COMMENT '프로젝트 변경 일자',
                            PRIMARY KEY (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='프로젝트';


-- gate.sdk_qr_codes definition

CREATE TABLE `sdk_qr_codes` (
                                `code` varchar(36) NOT NULL COMMENT 'QR 코드 UUID',
                                `token` text NOT NULL COMMENT 'QR 토큰 (JWT)',
                                `type` varchar(20) NOT NULL COMMENT 'QR 코드 유형 (CREATE_USER, VERIFY, IDENTIFY, LIVENESS)',
                                `expires_at` datetime NOT NULL COMMENT '만료 일시 (UTC)',
                                `is_used` tinyint(1) NOT NULL DEFAULT 0 COMMENT '사용 여부',
                                PRIMARY KEY (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='데모 QR 코드';


-- gate.users definition

CREATE TABLE `users` (
                         `user_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '사용자 식별 번호',
                         `project_id` bigint(20) NOT NULL COMMENT '프로젝트 식별 번호',
                         `face_id` varchar(100) NOT NULL COMMENT '사용자 페이스아이디',
                         `face_image_path` varchar(255) DEFAULT '' COMMENT '사용자 얼굴 이미지 경로',
                         `description` varchar(255) DEFAULT '' COMMENT '사용자 정보',
                         `is_deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '사용자 삭제 여부',
                         `transaction_uuid` varchar(36) NOT NULL COMMENT '요청 키',
                         `created_by` bigint(20) NOT NULL COMMENT '사용자 정보 생성자',
                         `created_at` datetime NOT NULL COMMENT '사용자 정보 생성 일자',
                         `updated_by` bigint(20) NOT NULL COMMENT '사용자 정보 변경자',
                         `updated_at` datetime NOT NULL COMMENT '사용자 정보 변경 일자',
                         PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;


-- gate.webhook_configs definition

CREATE TABLE `webhook_configs` (
                                   `webhook_config_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '웹훅 식별 번호',
                                   `project_id` bigint(20) NOT NULL COMMENT '프로젝트 식별 번호',
                                   `webhook_url` varchar(500) NOT NULL COMMENT '웹훅 URL',
                                   `demo_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '데모 웹훅 활성화 여부',
                                   `sdk_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'SDK 웹훅 활성화 여부',
                                   `api_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'API 웹훅 활성화 여부',
                                   `created_by` bigint(20) NOT NULL COMMENT '웹훅 생성자',
                                   `created_at` datetime NOT NULL COMMENT '웹훅 생성 일자',
                                   `updated_by` bigint(20) NOT NULL COMMENT '웹훅 변경자',
                                   `updated_at` datetime NOT NULL COMMENT '웹훅 변경 일자',
                                   PRIMARY KEY (`webhook_config_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;