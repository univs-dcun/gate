-- palm_feature: 누락된 JPA 감사(Audit) 컬럼 추가
ALTER TABLE palm_feature ADD created_by NUMBER DEFAULT NULL;
ALTER TABLE palm_feature ADD updated_by NUMBER DEFAULT NULL;
