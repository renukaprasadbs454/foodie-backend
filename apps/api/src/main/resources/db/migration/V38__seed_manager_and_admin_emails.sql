-- V38: Seed credentials for all Admin Roles in local development & testing

-- 1. Super Admin
INSERT INTO user_credential (id, phone_number, email, password_hash, user_type, active, created_at, updated_at)
VALUES ('33333333-3333-3333-3333-333333333001', '+919999999999', 'admin@foodie.local', '$2a$10$UAwCF/QkMLt.caFqCRE7yu3V4Yg3upmgTKSxT9N7PMEI2GcAqtfFy', 'ADMIN', TRUE, now(), now())
ON CONFLICT (id) DO UPDATE SET email = 'admin@foodie.local';

-- 2. Restaurant Manager
INSERT INTO user_credential (id, phone_number, email, password_hash, user_type, active, created_at, updated_at)
VALUES ('33333333-3333-3333-3333-333333333003', '+919999999997', 'manager@foodie.local', '$2a$10$UAwCF/QkMLt.caFqCRE7yu3V4Yg3upmgTKSxT9N7PMEI2GcAqtfFy', 'ADMIN', TRUE, now(), now())
ON CONFLICT (id) DO UPDATE SET email = 'manager@foodie.local';

INSERT INTO admin_user (id, user_credential_id, role_id, full_name, created_at, updated_at)
VALUES ('44444444-4444-4444-4444-444444444003', '33333333-3333-3333-3333-333333333003', '11111111-1111-1111-1111-111111111002', 'Restaurant Manager Admin', now(), now())
ON CONFLICT (id) DO NOTHING;

-- 3. Finance Admin
INSERT INTO user_credential (id, phone_number, email, password_hash, user_type, active, created_at, updated_at)
VALUES ('33333333-3333-3333-3333-333333333004', '+919999999996', 'finance@foodie.local', '$2a$10$UAwCF/QkMLt.caFqCRE7yu3V4Yg3upmgTKSxT9N7PMEI2GcAqtfFy', 'ADMIN', TRUE, now(), now())
ON CONFLICT (id) DO UPDATE SET email = 'finance@foodie.local';

INSERT INTO admin_user (id, user_credential_id, role_id, full_name, created_at, updated_at)
VALUES ('44444444-4444-4444-4444-444444444004', '33333333-3333-3333-3333-333333333004', '11111111-1111-1111-1111-111111111003', 'Finance Admin', now(), now())
ON CONFLICT (id) DO NOTHING;

-- 4. Operations Admin
INSERT INTO user_credential (id, phone_number, email, password_hash, user_type, active, created_at, updated_at)
VALUES ('33333333-3333-3333-3333-333333333005', '+919999999995', 'ops@foodie.local', '$2a$10$UAwCF/QkMLt.caFqCRE7yu3V4Yg3upmgTKSxT9N7PMEI2GcAqtfFy', 'ADMIN', TRUE, now(), now())
ON CONFLICT (id) DO UPDATE SET email = 'ops@foodie.local';

INSERT INTO admin_user (id, user_credential_id, role_id, full_name, created_at, updated_at)
VALUES ('44444444-4444-4444-4444-444444444005', '33333333-3333-3333-3333-333333333005', '11111111-1111-1111-1111-111111111002', 'Operations Admin', now(), now())
ON CONFLICT (id) DO NOTHING;

-- 5. Support Agent
INSERT INTO user_credential (id, phone_number, email, password_hash, user_type, active, created_at, updated_at)
VALUES ('33333333-3333-3333-3333-333333333006', '+919999999994', 'support@foodie.local', '$2a$10$UAwCF/QkMLt.caFqCRE7yu3V4Yg3upmgTKSxT9N7PMEI2GcAqtfFy', 'ADMIN', TRUE, now(), now())
ON CONFLICT (id) DO UPDATE SET email = 'support@foodie.local';

INSERT INTO admin_user (id, user_credential_id, role_id, full_name, created_at, updated_at)
VALUES ('44444444-4444-4444-4444-444444444006', '33333333-3333-3333-3333-333333333006', '11111111-1111-1111-1111-111111111004', 'Support Agent', now(), now())
ON CONFLICT (id) DO NOTHING;

-- 6. Compliance Auditor
INSERT INTO user_credential (id, phone_number, email, password_hash, user_type, active, created_at, updated_at)
VALUES ('33333333-3333-3333-3333-333333333007', '+919999999993', 'auditor@foodie.local', '$2a$10$UAwCF/QkMLt.caFqCRE7yu3V4Yg3upmgTKSxT9N7PMEI2GcAqtfFy', 'ADMIN', TRUE, now(), now())
ON CONFLICT (id) DO UPDATE SET email = 'auditor@foodie.local';

INSERT INTO admin_user (id, user_credential_id, role_id, full_name, created_at, updated_at)
VALUES ('44444444-4444-4444-4444-444444444007', '33333333-3333-3333-3333-333333333007', '11111111-1111-1111-1111-111111111001', 'Compliance Auditor', now(), now())
ON CONFLICT (id) DO NOTHING;

-- 7. Darkstore Admin
INSERT INTO user_credential (id, phone_number, email, password_hash, user_type, active, created_at, updated_at)
VALUES ('33333333-3333-3333-3333-333333333008', '+919999999992', 'darkstore@foodie.local', '$2a$10$UAwCF/QkMLt.caFqCRE7yu3V4Yg3upmgTKSxT9N7PMEI2GcAqtfFy', 'ADMIN', TRUE, now(), now())
ON CONFLICT (id) DO UPDATE SET email = 'darkstore@foodie.local';

INSERT INTO admin_user (id, user_credential_id, role_id, full_name, created_at, updated_at)
VALUES ('44444444-4444-4444-4444-444444444008', '33333333-3333-3333-3333-333333333008', '11111111-1111-1111-1111-111111111002', 'Darkstore Admin', now(), now())
ON CONFLICT (id) DO NOTHING;
