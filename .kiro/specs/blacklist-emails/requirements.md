# Requirements Document: Blacklist Emails Component

## Introduction

This document specifies the requirements for a global blacklist emails component that will be consumed by multiple services in the ecosystem (including MSCorreos) to verify if an email is blocked before sending notifications. The component is GLOBAL, without association to any company (no tenant_id), allowing centralized use by all services in the ecosystem.

## Glossary

- **BlacklistEmail**: Entity representing an email in the blacklist
- **BlacklistEmailService**: Service layer that handles business logic
- **BlacklistEmailRepository**: Data access layer for BlacklistEmail entities
- **BlacklistEmailCache**: Redis-based cache layer for email lookups
- **EmailNormalizado**: Email address converted to lowercase and trimmed
- **Activo**: Boolean flag indicating if the blacklist entry is active
- **Cache-Aside Pattern**: Caching strategy that first checks cache, then database

## Requirements

### Requirement 1: Email Blocking Check

**User Story:** As a system service, I want to verify if an email is blocked in the blacklist, so that I can prevent sending notifications to blocked recipients.

#### Acceptance Criteria

1. WHEN a client requests to check if an email is blocked, THE BlacklistEmailService SHALL first query the Redis cache
2. WHEN the cache contains the email status, THE BlacklistEmailService SHALL return the cached value without querying the database
3. WHEN the cache does not contain the email status, THE BlacklistEmailService SHALL query the database for the email with activo = true
4. WHEN the email is found with activo = true in the database, THE BlacklistEmailService SHALL return true and update the cache
5. WHEN the email is not found or activo = false, THE BlacklistEmailService SHALL return false and update the cache
6. THE BlacklistEmailService SHALL normalize all emails to lowercase and trimmed before any operation

### Requirement 2: Add Email to Blacklist

**User Story:** As an administrator, I want to add an email to the blacklist, so that blocked emails cannot receive notifications.

#### Acceptance Criteria

1. WHEN a client submits a valid email and reason to add to blacklist, THE BlacklistEmailService SHALL create a new BlacklistEmail record with activo = true
2. WHEN the email already exists in the blacklist with activo = true, THE BlacklistEmailService SHALL throw EmailYaExisteException
3. WHEN the email exists but activo = false, THE BlacklistEmailService SHALL reactivate the entry by setting activo = true and updating the reason
4. THE BlacklistEmailService SHALL set the bloqueado_por field with the user from the request header
5. THE BlacklistEmailService SHALL set the fecha_bloqueo to the current timestamp
6. THE BlacklistEmailService SHALL update the Redis cache with the new blocked status after successful operation

### Requirement 3: Remove Email from Blacklist

**User Story:** As an administrator, I want to remove an email from the blacklist, so that previously blocked emails can receive notifications again.

#### Acceptance Criteria

1. WHEN a client requests to remove an email from blacklist, THE BlacklistEmailService SHALL set activo = false for that email
2. WHEN the email does not exist in the blacklist, THE BlacklistEmailService SHALL throw EmailNoEncontradoException
3. THE BlacklistEmailService SHALL set the usuario_actualizacion and fecha_actualizacion fields
4. THE BlacklistEmailService SHALL invalidate the Redis cache entry for that email

### Requirement 4: List Blacklist Emails

**User Story:** As an administrator, I want to list all blocked emails with pagination, so that I can review the blacklist entries.

#### Acceptance Criteria

1. WHEN a client requests to list blacklist emails, THE BlacklistEmailService SHALL return a paginated list of emails where activo = true
2. THE BlacklistEmailService SHALL support sorting by fechaBloqueo (default) and pagination parameters (size, page)
3. THE response SHALL include id, email, razon, fechaBloqueo, bloqueadoPor, and activo fields

### Requirement 5: Count Active Blacklist Entries

**User Story:** As an administrator, I want to know the total count of active blocked emails, so that I can monitor the blacklist size.

#### Acceptance Criteria

1. WHEN a client requests the count of active blacklist entries, THE BlacklistEmailService SHALL return the count of records where activo = true

### Requirement 6: Cache Management

**User Story:** As a system architect, I want efficient cache management, so that the blacklist queries are optimized for performance.

#### Acceptance Criteria

1. THE BlacklistEmailCache SHALL store email status with a TTL of 24 hours
2. THE BlacklistEmailCache SHALL use the key prefix "blacklist:email:" for all cache entries
3. THE BlacklistEmailCache SHALL invalidate cache entries when emails are added or removed

### Requirement 7: Data Model Integrity

**User Story:** As a database administrator, I want data integrity in the blacklist table, so that duplicate active emails are prevented.

#### Acceptance Criteria

1. THE database SHALL enforce a unique constraint on email where activo = true
2. THE database SHALL have an index on the activo field for efficient filtering
3. THE database SHALL have an index on fecha_bloqueo for sorting operations

### Requirement 8: API Endpoints

**User Story:** As a developer, I want RESTful API endpoints to manage the blacklist, so that I can integrate with other services.

#### Acceptance Criteria

1. THE API SHALL provide GET /api/v1/blacklist-emails/{email} to check if an email is blocked
2. THE API SHALL provide POST /api/v1/blacklist-emails to add an email to the blacklist
3. THE API SHALL provide DELETE /api/v1/blacklist-emails/{email} to remove an email from the blacklist
4. THE API SHALL provide GET /api/v1/blacklist-emails to list emails with pagination
5. THE API SHALL provide GET /api/v1/blacklist-emails/count to get the count of active entries
6. THE API SHALL validate email format using @Email annotation on POST requests

### Requirement 9: Integration with MSCorreos

**User Story:** As a MSCorreos service, I want to check if an email is blocked before sending, so that I don't send notifications to blocked recipients.

#### Acceptance Criteria

1. THE MSCorreos service SHALL use a Feign client to call the blacklist service
2. THE MSCorreos service SHALL check the blacklist before sending any email notification
3. THE MSCorreos service SHALL skip sending when the email is found to be blocked