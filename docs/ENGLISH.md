# ZhuaTech ForgeFlow

ZhuaTech / 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · WeChat: zhuatech / zhuatech2.

A non-commercial source edition for small discrete manufacturing workshops and implementers. Written commercial authorization is required for commercial use or customer delivery; see LICENSE. Chinese and English operational UI are included. Record names are not machine translated.

Create materials, finished products and workstations; draft and activate a single-level BOM with sequential routing. Release an integer-piece production order, assign each operation, issue materials and start. Operators report only their assigned operations. Good output plus scrap must account for each operation's input; downstream input equals the previous completed operation's good output. An independent inspector records final acceptance. Storekeepers receive accepted output and its net material / actual labor cost, or close zero-output orders as a recorded loss without inventing stock.

Material quantities support six decimal places, rates four, hours three, currency two. Returns refer to the original issue and restore its original cost. Duplicate request keys prevent double issues and receipts. Departments, assignments and cost fields have separate server-side authorization.

Java 21, Spring Boot 4.0.7, Vue 3, Node 24.19+, MySQL 8.4, Flyway and Docker Compose v2. Run `python3 scripts/init-env.py`, then `docker compose --env-file .env up --build -d`. Open http://localhost:8099. Username defaults to admin; the privately generated ADMIN_PASSWORD is in your local .env, with no shared default password. Do not commit it. Empty database only: SEED_DEMO=true creates fictional master records and a draft BOM, with zero stock and no operators or orders. Existing data is not overwritten on restart.

This is not a complete ERP, automated MES, financial ledger, advanced scheduler or multi-level MRP system. No PLC, online payment, procurement or sales integrations are provided. Finished dispatch is a manual warehouse record. Lot/serial tracking, inventory reservation, multi-instance session storage and industrial-scale load testing are outside this release. See the operations, architecture, deployment and validation documents before adapting it for a real plant. Contact ZhuaTech for licensing, implementation and integrations.
