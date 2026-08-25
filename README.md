# NuKlean Backend

Spring Boot API backend for the NuKlean B2C ordering and tracking system, backed by PostgreSQL.

## 🚀 Running the App
1. Copy `.env.example` to `.env` and fill in the values.
2. Compile and run:
   ```bash
   ./mvnw spring-boot:run
   ```

## ⚠️ SECURITY WARNING & KEY ROTATION
If you are deploying this repository publicly or privately:
> [!IMPORTANT]
> **Key Rotation Warning:**
> If any environment keys, JWT secrets, database connection passwords, or external integrations were previously hardcoded in configuration files or seeding scripts:
> * **Rotate all credentials immediately.** 
> * Do not reuse any database password or JWT token committed in Git history.
