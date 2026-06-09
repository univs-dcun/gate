-- projects: project_type, package_key 컬럼 제거
ALTER TABLE projects DROP COLUMN project_type;
ALTER TABLE projects DROP COLUMN package_key;
