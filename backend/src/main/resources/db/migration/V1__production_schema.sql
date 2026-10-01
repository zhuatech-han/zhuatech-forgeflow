-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2

CREATE TABLE department (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  name varchar(120) NOT NULL UNIQUE
);

CREATE TABLE access_role (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  name varchar(120) NOT NULL UNIQUE,
  scope varchar(20) NOT NULL
);

CREATE TABLE permission (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  name varchar(120) NOT NULL
);

CREATE TABLE role_permission (role_id bigint NOT NULL, permission_code varchar(60) NOT NULL, PRIMARY KEY(role_id, permission_code), FOREIGN KEY(role_id) REFERENCES access_role(id), FOREIGN KEY(permission_code) REFERENCES permission(code));

CREATE TABLE nav_menu (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  name varchar(120) NOT NULL,
  name_en varchar(120) NOT NULL,
  permission_code varchar(60) NOT NULL,
  position int NOT NULL,
  enabled boolean NOT NULL
);

CREATE TABLE account (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  username varchar(60) NOT NULL UNIQUE,
  display_name varchar(120) NOT NULL,
  password_hash varchar(100) NOT NULL,
  role_id bigint NOT NULL,
  department_id bigint NOT NULL,
  enabled boolean NOT NULL,
  FOREIGN KEY (role_id) REFERENCES access_role(id),
  FOREIGN KEY (department_id) REFERENCES department(id)
);

CREATE TABLE audit_event (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  actor varchar(60) NOT NULL,
  action varchar(120) NOT NULL,
  object_id varchar(80) NOT NULL,
  department_id bigint NOT NULL,
  created_at timestamp(6) NOT NULL
);

CREATE TABLE system_setting (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  parameter_value varchar(200) NOT NULL
);

CREATE TABLE dictionary_entry (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  type varchar(60) NOT NULL,
  code varchar(60) NOT NULL,
  name varchar(120) NOT NULL,
  name_en varchar(120) NOT NULL,
enabled boolean NOT NULL DEFAULT TRUE,
  UNIQUE (type, code)
);


CREATE TABLE item (
id bigint AUTO_INCREMENT PRIMARY KEY,
code varchar(60) NOT NULL,
name varchar(120) NOT NULL,
kind varchar(60) NOT NULL,
unit varchar(60) NOT NULL,
specification varchar(400) NOT NULL,
department_id bigint NOT NULL,
FOREIGN KEY (department_id) REFERENCES department(id),
enabled boolean NOT NULL,
quantity decimal(20,6) NOT NULL,
inventory_value decimal(20,2) NOT NULL,
reorder_level decimal(20,6) NOT NULL,
UNIQUE(code),
CHECK(quantity>=0 AND inventory_value>=0 AND reorder_level>=0),
CHECK(kind IN ('MATERIAL','FINISHED'))
);

CREATE TABLE station (
id bigint AUTO_INCREMENT PRIMARY KEY,
code varchar(60) NOT NULL,
name varchar(120) NOT NULL,
department_id bigint NOT NULL,
FOREIGN KEY (department_id) REFERENCES department(id),
hourly_rate decimal(20,4) NOT NULL,
enabled boolean NOT NULL,
UNIQUE(code),
CHECK(hourly_rate>=0)
);

CREATE TABLE bill_of_materials (
id bigint AUTO_INCREMENT PRIMARY KEY,
product_id bigint NOT NULL,
FOREIGN KEY (product_id) REFERENCES item(id),
version_number integer NOT NULL,
department_id bigint NOT NULL,
FOREIGN KEY (department_id) REFERENCES department(id),
description varchar(2000) NOT NULL,
status varchar(60) NOT NULL,
revision bigint NOT NULL,
created_by varchar(60) NOT NULL,
created_at timestamp(6) NOT NULL,
updated_at timestamp(6) NOT NULL,
UNIQUE(product_id,version_number),
CHECK(version_number>0 AND revision>=0)
);

CREATE TABLE bom_component (
id bigint AUTO_INCREMENT PRIMARY KEY,
bom_id bigint NOT NULL,
FOREIGN KEY (bom_id) REFERENCES bill_of_materials(id),
item_id bigint NOT NULL,
FOREIGN KEY (item_id) REFERENCES item(id),
item_code varchar(60) NOT NULL,
item_name varchar(120) NOT NULL,
unit varchar(60) NOT NULL,
per_unit decimal(20,6) NOT NULL,
UNIQUE(bom_id,item_id),
CHECK(per_unit>0)
);

CREATE TABLE bom_operation (
id bigint AUTO_INCREMENT PRIMARY KEY,
bom_id bigint NOT NULL,
FOREIGN KEY (bom_id) REFERENCES bill_of_materials(id),
step_sequence integer NOT NULL,
station_id bigint NOT NULL,
FOREIGN KEY (station_id) REFERENCES station(id),
name varchar(120) NOT NULL,
station_name varchar(120) NOT NULL,
hourly_rate decimal(20,4) NOT NULL,
UNIQUE(bom_id,step_sequence),
CHECK(step_sequence>0 AND hourly_rate>=0)
);

CREATE TABLE production_order (
id bigint AUTO_INCREMENT PRIMARY KEY,
number varchar(60) NOT NULL,
bom_id bigint NOT NULL,
FOREIGN KEY (bom_id) REFERENCES bill_of_materials(id),
product_id bigint NOT NULL,
FOREIGN KEY (product_id) REFERENCES item(id),
department_id bigint NOT NULL,
FOREIGN KEY (department_id) REFERENCES department(id),
product_code varchar(60) NOT NULL,
product_name varchar(120) NOT NULL,
unit varchar(60) NOT NULL,
bom_version integer NOT NULL,
planned_quantity integer NOT NULL,
due_date date NOT NULL,
note varchar(2000) NOT NULL,
source_reference varchar(200) NOT NULL,
status varchar(60) NOT NULL,
revision bigint NOT NULL,
created_by varchar(60) NOT NULL,
created_at timestamp(6) NOT NULL,
updated_at timestamp(6) NOT NULL,
finished_at timestamp(6),
accepted_quantity integer NOT NULL,
receipt_reference varchar(200) NOT NULL,
material_variance_note varchar(2000) NOT NULL,
material_cost decimal(20,2) NOT NULL,
labor_cost decimal(20,2) NOT NULL,
total_cost decimal(20,2) NOT NULL,
loss_cost decimal(20,2) NOT NULL,
UNIQUE(number),
CHECK(planned_quantity>0 AND accepted_quantity>=0 AND revision>=0),
CHECK(material_cost>=0 AND labor_cost>=0 AND total_cost>=0 AND loss_cost>=0)
);

CREATE TABLE order_material (
id bigint AUTO_INCREMENT PRIMARY KEY,
order_id bigint NOT NULL,
FOREIGN KEY (order_id) REFERENCES production_order(id),
item_id bigint NOT NULL,
FOREIGN KEY (item_id) REFERENCES item(id),
item_code varchar(60) NOT NULL,
item_name varchar(120) NOT NULL,
unit varchar(60) NOT NULL,
required_quantity decimal(20,6) NOT NULL,
authorized_quantity decimal(20,6) NOT NULL,
adjustment_note varchar(2000) NOT NULL,
UNIQUE(order_id,item_id),
CHECK(required_quantity>0 AND authorized_quantity>=0)
);

CREATE TABLE work_step (
id bigint AUTO_INCREMENT PRIMARY KEY,
order_id bigint NOT NULL,
FOREIGN KEY (order_id) REFERENCES production_order(id),
step_sequence integer NOT NULL,
station_id bigint NOT NULL,
FOREIGN KEY (station_id) REFERENCES station(id),
name varchar(120) NOT NULL,
station_name varchar(120) NOT NULL,
hourly_rate decimal(20,4) NOT NULL,
operator_id bigint,
FOREIGN KEY (operator_id) REFERENCES account(id),
operator_name varchar(120) NOT NULL,
finished boolean NOT NULL,
UNIQUE(order_id,step_sequence),
CHECK(hourly_rate>=0)
);

CREATE TABLE production_report (
id bigint AUTO_INCREMENT PRIMARY KEY,
order_id bigint NOT NULL,
FOREIGN KEY (order_id) REFERENCES production_order(id),
step_id bigint NOT NULL,
FOREIGN KEY (step_id) REFERENCES work_step(id),
good_quantity integer NOT NULL,
rejected_quantity integer NOT NULL,
hours decimal(20,3) NOT NULL,
labor_cost decimal(20,2) NOT NULL,
actor_id bigint NOT NULL,
FOREIGN KEY (actor_id) REFERENCES account(id),
created_by varchar(60) NOT NULL,
reference varchar(200) NOT NULL,
note varchar(2000) NOT NULL,
voided boolean NOT NULL,
voided_by bigint,
FOREIGN KEY (voided_by) REFERENCES account(id),
void_reason varchar(2000) NOT NULL,
created_at timestamp(6) NOT NULL,
CHECK(good_quantity>=0 AND rejected_quantity>=0 AND hours>=0 AND labor_cost>=0)
);

CREATE TABLE quality_review (
id bigint AUTO_INCREMENT PRIMARY KEY,
order_id bigint NOT NULL,
FOREIGN KEY (order_id) REFERENCES production_order(id),
passed boolean NOT NULL,
observed_quantity integer NOT NULL,
accepted_quantity integer NOT NULL,
note varchar(2000) NOT NULL,
actor_id bigint NOT NULL,
FOREIGN KEY (actor_id) REFERENCES account(id),
created_by varchar(60) NOT NULL,
created_at timestamp(6) NOT NULL,
CHECK(accepted_quantity>=0 AND observed_quantity>=accepted_quantity)
);

CREATE TABLE stock_movement (
id bigint AUTO_INCREMENT PRIMARY KEY,
item_id bigint NOT NULL,
FOREIGN KEY (item_id) REFERENCES item(id),
department_id bigint NOT NULL,
FOREIGN KEY (department_id) REFERENCES department(id),
order_id bigint,
FOREIGN KEY (order_id) REFERENCES production_order(id),
material_id bigint,
FOREIGN KEY (material_id) REFERENCES order_material(id),
source_id bigint,
FOREIGN KEY (source_id) REFERENCES stock_movement(id),
kind varchar(60) NOT NULL,
quantity decimal(20,6) NOT NULL,
quantity_delta decimal(20,6) NOT NULL,
value_delta decimal(20,2) NOT NULL,
balance_quantity decimal(20,6) NOT NULL,
balance_value decimal(20,2) NOT NULL,
reference varchar(200) NOT NULL,
note varchar(2000) NOT NULL,
actor_id bigint NOT NULL,
FOREIGN KEY (actor_id) REFERENCES account(id),
created_by varchar(60) NOT NULL,
created_at timestamp(6) NOT NULL,
CHECK(quantity>=0 AND balance_quantity>=0 AND balance_value>=0)
);

CREATE TABLE business_event (
id bigint AUTO_INCREMENT PRIMARY KEY,
order_id bigint NOT NULL,
FOREIGN KEY (order_id) REFERENCES production_order(id),
kind varchar(60) NOT NULL,
note varchar(2000) NOT NULL,
actor_id bigint NOT NULL,
FOREIGN KEY (actor_id) REFERENCES account(id),
created_by varchar(60) NOT NULL,
created_at timestamp(6) NOT NULL
);

CREATE TABLE mutation_stamp (id bigint AUTO_INCREMENT PRIMARY KEY, request_key varchar(80) NOT NULL UNIQUE, fingerprint varchar(64) NOT NULL, result_id bigint);
CREATE INDEX ix_production_scope ON production_order(department_id,status,due_date,id);
CREATE INDEX ix_step_operator ON work_step(operator_id,order_id);
CREATE INDEX ix_stock_item ON stock_movement(item_id,id);
CREATE INDEX ix_stock_order ON stock_movement(order_id,id);
CREATE INDEX ix_report_order ON production_report(order_id,step_id,id);
CREATE INDEX ix_quality_order ON quality_review(order_id,id);
CREATE INDEX ix_event_order ON business_event(order_id,id);
