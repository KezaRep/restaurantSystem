# Aiven integration (Java 21)

1. Run `migration.sql` **only if** `order_items.note` does not exist. Do not import the original dump over existing data.
2. Download official MySQL Connector/J JAR and add to Eclipse project **Classpath** (not mysql-server ZIP). If Eclipse uses Modulepath, add `requires com.mysql.cj;` in module-info.java.
3. Set `DB_HOST`, `DB_PORT`, `DB_NAME=restaurant_db`, `DB_USER`, `DB_PASSWORD` in Eclipse Run Configurations -> Environment for **RestaurantServer**. Do not commit credentials.
4. Start `server.RestaurantServer` (TCP 5001), then `client.StaffClient` and `client.KitchenClient`. Set host to the server's reachable IP (not localhost for remote clients). For remote networks use a VPN such as Tailscale; do not expose unauthenticated TCP directly to the internet.
5. Server seeds 30 tables and inserts menu dishes by name if absent. Existing foods with the same name are reused; for consistency set prices in Aiven to match `model.Menu` before testing.
6. This release retains the existing per-dish CREATE_ORDER protocol and GUI. Each dish creates one orders row and one order_items row; payment creates one payments row per order. New UUID request IDs are sent by StaffClient but the legacy protocol does not persist/retry IDs across application restarts. The in-memory menu remains a client-side catalog; DB is authoritative for availability and historical item prices at order creation.
7. Check database privileges for SELECT/INSERT/UPDATE. For production configure Aiven CA certificate and `sslMode=VERIFY_IDENTITY`; REQUIRED encrypts traffic without host verification.
8. Existing TCP REGISTER is role declaration, **not authentication**. Use only on trusted/VPN networks until authentication is implemented.
